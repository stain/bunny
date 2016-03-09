package org.rabix.engine.event.impl;

import org.rabix.engine.event.Event;

/**
 * This event is used to update one output (per port) for the specific Job. Potentially, it can produce one ore more output and inputs events. 
 */
public class OutputUpdateEvent implements Event {

  private final String jobId;
  private final String contextId;
  
  private final Object value;
  private final String portId;
  
  private final boolean fromScatter;        // it's a scatter event
  private final Integer scatteredNodes;     // number of scattered nodes

  public OutputUpdateEvent(String contextId, String jobId, String portId, Object outputValue) {
    this.jobId = jobId;
    this.contextId = contextId;
    this.portId = portId;
    this.value = outputValue;
    this.fromScatter = false;
    this.scatteredNodes = null;
  }
  
  public OutputUpdateEvent(String contextId, String jobId, String portId, Object outputValue, boolean fromScatter, Integer scatteredNodes) {
    this.jobId = jobId;
    this.contextId = contextId;
    this.portId = portId;
    this.value = outputValue;
    this.fromScatter = fromScatter;
    this.scatteredNodes = scatteredNodes;
  }
  
  public String getJobId() {
    return jobId;
  }
  
  public Object getValue() {
    return value;
  }
  
  public String getPortId() {
    return portId;
  }

  public boolean isFromScatter() {
    return fromScatter;
  }
  
  public Integer getScatteredNodes() {
    return scatteredNodes;
  }
  
  @Override
  public String getContextId() {
    return contextId;
  }
  
  @Override
  public EventType getType() {
    return EventType.OUTPUT_UPDATE;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((contextId == null) ? 0 : contextId.hashCode());
    result = prime * result + (fromScatter ? 1231 : 1237);
    result = prime * result + ((jobId == null) ? 0 : jobId.hashCode());
    result = prime * result + ((portId == null) ? 0 : portId.hashCode());
    result = prime * result + ((scatteredNodes == null) ? 0 : scatteredNodes.hashCode());
    result = prime * result + ((value == null) ? 0 : value.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    OutputUpdateEvent other = (OutputUpdateEvent) obj;
    if (contextId == null) {
      if (other.contextId != null)
        return false;
    } else if (!contextId.equals(other.contextId))
      return false;
    if (fromScatter != other.fromScatter)
      return false;
    if (jobId == null) {
      if (other.jobId != null)
        return false;
    } else if (!jobId.equals(other.jobId))
      return false;
    if (portId == null) {
      if (other.portId != null)
        return false;
    } else if (!portId.equals(other.portId))
      return false;
    if (scatteredNodes == null) {
      if (other.scatteredNodes != null)
        return false;
    } else if (!scatteredNodes.equals(other.scatteredNodes))
      return false;
    if (value == null) {
      if (other.value != null)
        return false;
    } else if (!value.equals(other.value))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "OutputUpdateEvent [jobId=" + jobId + ", contextId=" + contextId + ", portId=" + portId + ", value=" + value + ", fromScatter=" + fromScatter + ", scatteredNodes=" + scatteredNodes + "]";
  }
  
}
