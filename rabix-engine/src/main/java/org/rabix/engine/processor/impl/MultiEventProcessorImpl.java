package org.rabix.engine.processor.impl;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.configuration.Configuration;
import org.rabix.engine.event.Event;
import org.rabix.engine.processor.EventProcessor;
import org.rabix.engine.processor.handler.EventHandlerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class MultiEventProcessorImpl implements EventProcessor {

  private final static Logger logger = LoggerFactory.getLogger(MultiEventProcessorImpl.class);
  
  private int eventProcessorCount;
  
  private final ConcurrentMap<Integer, EventProcessorImpl> eventProcessors;

  private volatile boolean isRunning = false;
  
  @Inject
  public MultiEventProcessorImpl(Provider<EventProcessorImpl> singleEventProcessorProvider, Configuration configuration) {
    this.eventProcessorCount = configuration.getInt("engine.event_processor.count", Runtime.getRuntime().availableProcessors());
    this.eventProcessors = new ConcurrentHashMap<>(eventProcessorCount);
    for (int i = 0; i < eventProcessorCount; i++) {
      this.eventProcessors.put(i, singleEventProcessorProvider.get());
    }
    start();
  }

  @Override
  public void start() {
    for (EventProcessorImpl singleEventProcessor : eventProcessors.values()) {
      singleEventProcessor.start();
    }
    this.isRunning = true;
  }

  @Override
  public void stop() {
    for (EventProcessorImpl eventProcessor : eventProcessors.values()) {
      eventProcessor.stop();
    }
    this.isRunning = false;
  }

  @Override
  public void send(Event event) throws EventHandlerException {
    getEventProcessor(event.getContextId()).send(event);
  }

  @Override
  public void addToQueue(Event event) {
    getEventProcessor(event.getContextId()).addToQueue(event);
  }
  
  @Override
  public void persist(Event event) {
    getEventProcessor(event.getContextId()).persist(event);
  }
  
  @Override
  public void addToExternalQueue(Event event) {
    getEventProcessor(event.getContextId()).addToExternalQueue(event);
  }
  
  @Override
  public boolean isRunning() {
    return isRunning;
  }

  /**
   * Gets {@link EventProcessor} based on Root ID
   * TODO: discuss load balancing algorithm 
   * 
   * @param rootId  Root ID
   * @return        EventProcessor instance
   */
  private EventProcessor getEventProcessor(UUID rootId) {
    int index = EventProcessorDispatcher.dispatch(rootId, eventProcessorCount);
    logger.debug("Root Job {} goes to EventProcessor {}", rootId, index);
    return eventProcessors.get(index);
  }

}
