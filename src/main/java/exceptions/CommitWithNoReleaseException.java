package exceptions;

public class CommitWithNoReleaseException extends Exception{
    public CommitWithNoReleaseException(String msg){
        super(msg);
    }
}
