public class ResponseMessage {
    private int response;
    private String errorMessage;

    public ResponseMessage(int r, String em){
        this.response = r;
        this.errorMessage = em;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String toString(){
        return response+"-"+errorMessage;
    }
}
