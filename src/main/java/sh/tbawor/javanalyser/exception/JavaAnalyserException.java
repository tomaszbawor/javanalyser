package sh.tbawor.javanalyser.exception;

/**
 * Base exception class for all exceptions in the JavaAnalyser application.
 * Provides a consistent exception hierarchy for different types of errors.
 */
public class JavaAnalyserException extends RuntimeException {

    public JavaAnalyserException(String message) {
        super(message);
    }

    public JavaAnalyserException(String message, Throwable cause) {
        super(message, cause);
    }
}