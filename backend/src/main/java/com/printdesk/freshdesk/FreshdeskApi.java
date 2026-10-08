package com.printdesk.freshdesk;

import java.util.Map;

/** The few Freshdesk REST v2 calls PrintDesk needs. {@link HttpFreshdeskApi} talks to Freshdesk; tests use a fake. */
public interface FreshdeskApi {

    /** GET a path such as {@code /api/v2/tickets/42}. Returns a JSON object or a JSON array (as List). */
    Object get(String path) throws FreshdeskException;

    Map<String, Object> post(String path, Map<String, Object> body) throws FreshdeskException;

    Map<String, Object> put(String path, Map<String, Object> body) throws FreshdeskException;

    final class FreshdeskException extends Exception {
        public final int status;

        public FreshdeskException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
