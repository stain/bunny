package org.rabix.executor.rest;

import org.eclipse.jetty.server.Server;

public class ExecutorRestEntry {

  public static void main(String[] args) throws Exception {
    Server server = new ServerBuilder().build();
    try {
      server.start();
      server.join();
    }
    finally {
      server.destroy();
    }
  }

}
