package sh.tbawor.javanalyser.exception;

/**
 * Exception thrown when there is an error generating or retrieving embeddings.
 */
public class EmbeddingException extends JavaAnalyserException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}