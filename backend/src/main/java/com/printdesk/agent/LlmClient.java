package com.printdesk.agent;

import java.util.Map;

/**
 * One call to the Claude Messages API. The request and response are the API's own JSON
 * shapes as maps, so the agent loop can be tested with a scripted fake (no network).
 */
public interface LlmClient {
    Map<String, Object> createMessage(Map<String, Object> request) throws LlmException;

    final class LlmException extends Exception {
        public final int status;
        public LlmException(String msg, int status) {
            super(msg);
            this.status = status;
        }
    }
}
