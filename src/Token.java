public class Token {
    private String tokenType;
    private String tokenValue;

    private int lineCol;
    private int lineRow;

    private Token next = null;

    public Token(String tokenType, String tokenValue, int lineCol, int lineRow){
        this.tokenType = tokenType;
        this.tokenValue = tokenValue;

        this.lineCol = lineCol;
        this.lineRow = lineRow;
    }

    @Override
    public String toString(){
        return tokenValue;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public void setTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }

    public int getLineCol() {
        return lineCol;
    }

    public void setLineCol(int lineCol) {
        this.lineCol = lineCol;
    }

    public int getLineRow() {
        return lineRow;
    }

    public void setLineRow(int lineRow) {
        this.lineRow = lineRow;
    }

    public Token getNext() {
        return next;
    }

    public void setNext(Token next) {
        this.next = next;
    }
}
