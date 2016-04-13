public final class Token {
    private String tokenType= "";
    private String tokenValue = "";

    private int lineCol = 0;
    private int lineRow = 0;

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

//    public boolean equals(Token rhs) {
//        return (this.getTokenType().equals(rhs.getTokenType()) && (this.getTokenValue().equals(rhs.getTokenValue())));
//    }

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
}
