package sh.tbawor.javanalyser.exception;

/**
 * Exception thrown when there is an error processing code queries.
 */
public class QueryException extends JavaAnalyserException {

    public QueryException(String message) {
        super(message);
    }

    public QueryException(String message, Throwable cause) {
        super(message, cause);
    }
}