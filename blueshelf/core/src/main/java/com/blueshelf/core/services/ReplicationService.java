package com.blueshelf.core.services;

/**
 * Moves content from author to publish.
 *
 * Correlation: AEM's {@code com.day.cq.replication.Replicator#replicate(session, ReplicationActionType, path)}
 * backed by "replication agents" (/etc/replication/agents.author/publish) that push content over HTTP.
 * In AEM as a Cloud Service this is Sling Content Distribution under the hood. We implement the same idea:
 * serialize the page subtree and push it to the publish instance over HTTP.
 */
public interface ReplicationService {

    enum Action { ACTIVATE, DEACTIVATE }

    /**
     * @param resolver the caller's resolver (we read with the user's rights; AEM would use the replication
     *                 service user — gotcha: never use the deprecated administrative resolver)
     * @return human-readable result, throws on failure
     */
    String replicate(org.apache.sling.api.resource.ResourceResolver resolver, String path, Action action) throws ReplicationException;

    class ReplicationException extends Exception {
        public ReplicationException(String message, Throwable cause) { super(message, cause); }
        public ReplicationException(String message) { super(message); }
    }
}
