package org.rabix.engine.processor.impl;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.rabix.bindings.model.Job;
import org.rabix.bindings.model.Job.JobStatus;
import org.rabix.common.helper.JSONHelper;
import org.rabix.engine.event.Event;
import org.rabix.engine.event.Event.EventType;
import org.rabix.engine.event.impl.ContextStatusEvent;
import org.rabix.engine.event.impl.InitEvent;
import org.rabix.engine.event.impl.JobStatusEvent;
import org.rabix.engine.processor.EventProcessor;
import org.rabix.engine.processor.handler.EventHandlerException;
import org.rabix.engine.processor.handler.HandlerFactory;
import org.rabix.engine.service.ContextRecordService;
import org.rabix.engine.service.JobService;
import org.rabix.engine.status.EngineStatusCallback;
import org.rabix.engine.status.EngineStatusCallbackException;
import org.rabix.engine.store.model.ContextRecord;
import org.rabix.engine.store.model.ContextRecord.ContextStatus;
import org.rabix.engine.store.model.EventRecord;
import org.rabix.engine.store.model.JobRecord.JobState;
import org.rabix.engine.store.repository.EventRepository;
import org.rabix.engine.store.repository.JobRepository;
import org.rabix.engine.store.repository.TransactionHelper;
import org.rabix.engine.store.repository.TransactionHelper.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * Event processor implementation
 */
public class EventProcessorImpl implements EventProcessor {

  private static final Logger logger = LoggerFactory.getLogger(EventProcessorImpl.class);
    
  private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
  private final BlockingQueue<Event> externalEvents = new LinkedBlockingQueue<>();
  
  private final ExecutorService executorService = Executors.newSingleThreadExecutor((Runnable r) -> {
    return new Thread(r, "EventProcessorThread" + r.hashCode());
  });
  
  private final AtomicBoolean stop = new AtomicBoolean(false);
  private final AtomicBoolean running = new AtomicBoolean(false);

  private final HandlerFactory handlerFactory;
  
  private final ContextRecordService contextRecordService;
  
  private final TransactionHelper transactionHelper;
  private final JobRepository jobRepository;
  private final EventRepository eventRepository;
  private final EngineStatusCallback callback;
  
  @Inject
  public EventProcessorImpl(HandlerFactory handlerFactory, ContextRecordService contextRecordService,
      TransactionHelper transactionHelper, EventRepository eventRepository,
      JobRepository jobRepository, EngineStatusCallback callback) {
    this.handlerFactory = handlerFactory;
    this.contextRecordService = contextRecordService;
    this.transactionHelper = transactionHelper;
    this.eventRepository = eventRepository;
    this.jobRepository = jobRepository;
    this.callback = callback;
  }

  public void start() {
    executorService.execute(new Runnable() {
      @Override
      public void run() {
        final AtomicReference<Event> eventReference = new AtomicReference<Event>(null);
        while (!stop.get()) {
          try {
            eventReference.set(externalEvents.take());
            running.set(true);
            transactionHelper.doInTransaction(new TransactionHelper.TransactionCallback<Void>() {
              @Override
              public Void call() throws TransactionException {
                if (!handle(eventReference.get())) {
                  eventRepository.deleteGroup(eventReference.get().getEventGroupId());
                  return null;
                }
                if (checkForReadyJobs(eventReference.get())) {
                  Set<Job> readyJobs = jobRepository.getReadyJobsByGroupId(eventReference.get().getEventGroupId());
                  try {
                    callback.onJobsReady(readyJobs, eventReference.get().getContextId(), eventReference.get().getProducedByNode());
                  } catch (EngineStatusCallbackException e) {
                    logger.error("Failed to ready jobs after event {}, error: {}", eventReference.get(), e);
                  }  
                }
                eventRepository.deleteGroup(eventReference.get().getEventGroupId());
                return null;
              }
            });
          } catch (Exception e) {
            logger.error("EventProcessor failed to process event {}.", eventReference.get(), e);
            try {
              Job job = jobRepository.get(eventReference.get().getContextId());
              job = Job.cloneWithMessage(job, "EventProcessor failed to process event:\n" + eventReference.get().toString());
//              jobRepository.update(job);
              callback.onJobRootFailed(job);
            } catch (Exception ex) {
              logger.error("Failed to call jobFailed handler for job after event {} failed.", e, ex);
            }
            try {
              Event event = eventReference.get();
              EventRecord er = new EventRecord(event.getEventGroupId(), EventRecord.Status.FAILED, JSONHelper.convertToMap(e));
              eventRepository.insert(er);
              invalidateContext(eventReference.get().getContextId());
            } catch (Exception ehe) {
              logger.error("Failed to invalidate Context {}.", eventReference.get().getContextId(), ehe);
            }
          }
        }
      }
    });
  }
  
  private boolean checkForReadyJobs(Event event) {
    return (event instanceof InitEvent || (event instanceof JobStatusEvent && ((JobStatusEvent) event).getState().equals(JobState.COMPLETED)));
  }
  
  private boolean handle(Event event) throws TransactionException {
    while (event != null) {
      try {
        handlerFactory.get(event.getType()).handle(event);
      } catch (EventHandlerException e) {
        throw new TransactionException(e);
      }
      event = events.poll();
    }
    return true;
  }
  
  /**
   * Invalidates context 
   */
  private void invalidateContext(UUID contextId) throws EventHandlerException {
    handlerFactory.get(Event.EventType.CONTEXT_STATUS_UPDATE).handle(new ContextStatusEvent(contextId, ContextStatus.FAILED));
  }
  
  @Override
  public void stop() {
    stop.set(true);
    running.set(false);
  }

  public boolean isRunning() {
    return running.get();
  }

  public void send(Event event) throws EventHandlerException {
    if (stop.get()) {
      return;
    }
    if (event.getType().equals(EventType.INIT)) {
      addToQueue(event);
      return;
    }
    handlerFactory.get(event.getType()).handle(event);
  }

  public void addToQueue(Event event) {
    if (stop.get()) {
      return;
    }
    this.events.add(event);
  }
  
  @Override
  public void persist(Event event) {
    if (stop.get()) {
      return;
    }
    EventRecord er = new EventRecord(event.getEventGroupId(), EventRecord.Status.UNPROCESSED, JSONHelper.convertToMap(event));
    eventRepository.insert(er);
  }
  
  public void addToExternalQueue(Event event) {
    if (stop.get()) {
      return;
    }
    this.externalEvents.add(event);
  }

}
