package sh.tbawor.javanalyser.exception;

/**
 * Exception thrown when there is an error parsing Java source files.
 */
public class ParsingException extends JavaAnalyserException {

    public ParsingException(String message) {
        super(message);
    }

    public ParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}