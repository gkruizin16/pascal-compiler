import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

// GJK
public final class Parser {
    enum TYPE {
        I, R, B, C, S, P, L, A     // integer, real, boolean, char, string, procedure, label, array
    }

    private static int dp = 0; // data pointer for vars

    private static final HashMap<String, TYPE> STRING_TYPE_HASH_MAP;
    static {
        STRING_TYPE_HASH_MAP = new HashMap<>();
        STRING_TYPE_HASH_MAP.put("integer", TYPE.I);
        STRING_TYPE_HASH_MAP.put("real", TYPE.R);
        STRING_TYPE_HASH_MAP.put("boolean", TYPE.B);
        STRING_TYPE_HASH_MAP.put("char", TYPE.C);
        STRING_TYPE_HASH_MAP.put("string", TYPE.S);
        STRING_TYPE_HASH_MAP.put("array", TYPE.A);

    }

    enum OP_CODE {
        PUSHI, PUSH, POP,PUSHF,
        JMP, JFALSE, JTRUE,
        CVR, CVI,
        DUP, XCHG, REMOVE,
        ADD, SUB, MULT, DIV, NEG,
        OR, AND,
        FADD, FSUB, FMULT, FDIV, FNEG,
        EQL, NEQL, GEQ, LEQ, GTR, LSS,
        FGTR, FLSS,
        HALT,
        PRINT_INT, PRINT_CHAR, PRINT_BOOL, PRINT_REAL, PRINT_NEWLINE,
        GET, PUT
    }

    private static final int ADDRESS_SIZE = 4;

    private static Token currentToken;
    private static Iterator<Token> it;

    private static final int INSTRUCTION_SIZE = 1000;

    private static Byte[] byteArray = new Byte[INSTRUCTION_SIZE];
    private static int ip = 0;

    public static Byte[] parse() {
        getToken(); // Get initial token

        match("TK_PROGRAM");
        match("TK_IDENTIFIER");
        match("TK_SEMI_COLON");

        program();

        return byteArray;
    }

    /*
    <pascal program> ->
	    [<program stat>]
	    <declarations>
	    <begin-statement>.
    <program stat> -> E
     */
    public static void program() {
        declarations();
        begin();
    }

    /*
    <declarations> ->
	    <var decl><declarations>
	    <label ______,,______>
	    <type ______,,______>
	    <const ______,,______>
	    <procedure ______,,______>
	    <function ______,,______>
	-> E
     */
    public static void declarations() {
        while (true) {
            switch (currentToken.getTokenType()) {
                case "TK_VAR":
                    varDeclarations();
                    break;
                case "TK_PROCEDURE":
                    procDeclaration();
                    break;
                case "TK_LABEL":
                    labelDeclarations();
                    break;
                case "TK_BEGIN":
                    return;
            }
        }
    }

    // label <namelist>;
    private static void labelDeclarations() {
        while(true) {
            if ("TK_LABEL".equals(currentToken.getTokenType())) {
                match("TK_LABEL");
            } else {
                // currentToken is not "TK_LABEL"
                break;
            }

            // Store labels in a list
            ArrayList<Token> labelsArrayList = new ArrayList<>();

            while ("TK_IDENTIFIER".equals(currentToken.getTokenType())) {
                currentToken.setTokenType("TK_A_LABEL");
                labelsArrayList.add(currentToken);

                match("TK_A_LABEL");

                if ("TK_COMMA".equals(currentToken.getTokenType())) {
                    match("TK_COMMA");
                }
            }

            // insert all labels into SymbolTable
            for (Token label : labelsArrayList) {


                Symbol symbol = new Symbol(label.getTokenValue(),
                        "TK_A_LABEL",
                        TYPE.L,
                        0);

                if (SymbolTable.lookup(label.getTokenValue()) == null) {
                    SymbolTable.insert(symbol);
                }
            }

            match("TK_SEMI_COLON");
        }
    }

    /*
    <procedure decl> -> procedure <name> [params];
        <declarations>
        <begin-statement>
            <statement> -> <procedure call>
    */
    private static void procDeclaration() {
        // declaration
        if (currentToken.getTokenType().equals("TK_PROCEDURE")) {
            match("TK_PROCEDURE");
            currentToken.setTokenType("TK_A_PROC");

            String procedureName = currentToken.getTokenValue();

            match("TK_A_PROC");
            match("TK_SEMI_COLON");

            // generate hole to jump past the body
            genOpCode(OP_CODE.JMP);
            int hole = ip;
            genAddress(0);

            Symbol symbol = new Symbol(procedureName,
                    "TK_A_PROC",
                    TYPE.P,
                    ip);

            // body
            match("TK_BEGIN");
            statements();
            match("TK_END");
            match("TK_SEMI_COLON");

            // hole to return the procedure
            genOpCode(OP_CODE.JMP);
            symbol.setReturnAddress(ip);
            genAddress(0);

            if (SymbolTable.lookup(procedureName) == null) {
                SymbolTable.insert(symbol);
            }

            // fill in the hole to jump past the body
            int save = ip;

            ip = hole;
            genAddress(save);
            ip = save;
        }
    }


    /*
    <var decl> ->
        var[<namelist>: <type>;]^+
     */
    public static void varDeclarations() {
        while(true) {
            if ("TK_VAR".equals(currentToken.getTokenType())) {
                match("TK_VAR");
            } else {
                // currentToken is not "TK_VAR"
                break;
            }

            // Store variables in a list
            ArrayList<Token> variablesArrayList = new ArrayList<>();

            while ("TK_IDENTIFIER".equals(currentToken.getTokenType())) {
                currentToken.setTokenType("TK_A_VAR");
                variablesArrayList.add(currentToken);

                match("TK_A_VAR");

                if ("TK_COMMA".equals(currentToken.getTokenType())) {
                    match("TK_COMMA");
                }
            }

            match("TK_COLON");
            String dataType = currentToken.getTokenType();
            match(dataType);

            // Add the correct datatype for each identifier and insert into symbol table
            for (Token var : variablesArrayList) {

                Symbol symbol = new Symbol(var.getTokenValue(),
                        "TK_A_VAR",
                        STRING_TYPE_HASH_MAP.get(dataType.toLowerCase().substring(3)),
                        dp);

                dp += 4;


                if (SymbolTable.lookup(var.getTokenValue()) == null) {
                    SymbolTable.insert(symbol);
                }
            }

            if (dataType.equals("TK_ARRAY")){
                arrayDeclaration(variablesArrayList);
            }

            match("TK_SEMI_COLON");

        }
    }


    /*
    <var decl> -> var <namelist>: <type>
    	<type> -> integer | real | bool | char
				<array type>
	    <array type> -> array[<low>..<high>]
				of <type> (simple type, array not allowed)


	    <low>,<high> ->
            ordinal constants of the same type
     */
    private static void arrayDeclaration(ArrayList<Token> variablesArrayList) {
        match("TK_OPEN_SQUARE_BRACKET");
        String v1 = currentToken.getTokenValue();
        TYPE indexType1 = getLitType(currentToken.getTokenType());
        match(currentToken.getTokenType());

        match("TK_RANGE");

        String v2 = currentToken.getTokenValue();
        TYPE indexType2 = getLitType(currentToken.getTokenType());
        match(currentToken.getTokenType());
        match("TK_CLOSE_SQUARE_BRACKET");
        match("TK_OF");

        String valueType = currentToken.getTokenType();
        match(valueType);

        if (indexType1 != indexType2){
            throw new Error(String.format("Array index LHS type (%s) is not equal to RHS type: (%s)", indexType1, indexType2));
        } else {

            assert indexType1 != null;
            switch (indexType1) {
                case I:
                    int i1 = Integer.valueOf(v1);
                    int i2 = Integer.valueOf(v2);
                    if (i1 > i2){
                        throw new Error(String.format("Array range is invalid: %d..%d", i1, i2));
                    }

                    Symbol firstIntArray = SymbolTable.lookup(variablesArrayList.get(0).getTokenValue());
                    if (firstIntArray != null) {
                        dp = firstIntArray.getAddress();
                    }

                    for (Token var: variablesArrayList) {
                        Symbol symbol = SymbolTable.lookup(var.getTokenValue());
                        if (symbol != null){

                            int elementSize = 4;
                            int size = elementSize*(i2 - i1 + 1);

                            symbol.setAddress(dp);
                            symbol.setLow(i1);
                            symbol.setHigh(i2);
                            symbol.setTokenType("TK_AN_ARRAY");
                            symbol.setIndexType(TYPE.I);
                            symbol.setValueType(STRING_TYPE_HASH_MAP.get(valueType.toLowerCase().substring(3)));

                            dp += size;
                        }
                    }

                    break;
                case C:
                    char c1 = v1.toCharArray()[0];
                    char c2 = v2.toCharArray()[0];
                    if (c1 > c2){
                        throw new Error(String.format("Array range is invalid: %c..%c", c1, c2));
                    }

                    Symbol firstCharArray = SymbolTable.lookup(variablesArrayList.get(0).getTokenValue());
                    if (firstCharArray != null) {
                        dp = firstCharArray.getAddress();
                    }

                    for (Token var: variablesArrayList) {
                        Symbol symbol = SymbolTable.lookup(var.getTokenValue());
                        if (symbol != null){
                            int size = c2 - c1 + 1;

                            symbol.setAddress(dp);
                            symbol.setLow(c1);
                            symbol.setHigh(c2);
                            symbol.setTokenType("TK_AN_ARRAY");
                            symbol.setIndexType(TYPE.C);
                            symbol.setValueType(STRING_TYPE_HASH_MAP.get(valueType.toLowerCase().substring(3)));

                            dp += size;
                        }
                    }

                    break;
                case R:
                    throw new Error("Array index type: real is invalid");
            }

        }

    }

    /*
    <begin_statement> ->
        begin <stats> end
     */
    public static void begin(){
        match("TK_BEGIN");
        statements();
        match("TK_END");
        match("TK_DOT");
        match("TK_EOF");
        genOpCode(OP_CODE.HALT);
    }

    /*
    <stats> ->
	    <while stat>; <stats>
	    <repeat ...
	    <goto ...
	    <case ...
	    <if ...
	    <for ...
	    <assignment> TK_A_VAR
	    <labelling> TK_A_LABEL
	    <procedure call> TK_A_PROC
	    <writeStat>
     */
    public static void statements(){
        while(!currentToken.getTokenType().equals("TK_END")) {
            switch (currentToken.getTokenType()) {
                case "TK_CASE":
                    caseStat();
                    break;
                case "TK_GOTO":
                    goToStat();
                    break;
                case "TK_WHILE":
                    whileStat();
                    break;
                case "TK_REPEAT":
                    repeatStat();
                    break;
                case "TK_IF":
                    ifStat();
                    break;
                case "TK_FOR":
                    forStat();
                    break;
                case "TK_WRITELN":
                    writeStat();
                    break;
                case "TK_IDENTIFIER":
                    Symbol symbol = SymbolTable.lookup(currentToken.getTokenValue());
                    if (symbol != null) {
                        // assign token type to be var, proc, or label
                        currentToken.setTokenType(symbol.getTokenType());
                    }
                    break;
                case "TK_A_VAR":
                    assignmentStat();
                    break;
                case "TK_A_PROC":
                    procedureStat();
                    break;
                case "TK_A_LABEL":
                    labelStat();
                    break;
                case "TK_AN_ARRAY":
                    arrayAssignmentStat();
                    break;
                case "TK_SEMI_COLON":
                    match("TK_SEMI_COLON");
                    break;
                default:
                    return;
            }
        }

    }

    private static void labelStat() {
        Symbol symbol = SymbolTable.lookup(currentToken.getTokenValue());
        match("TK_A_LABEL");
        match("TK_COLON");
        if (symbol != null) {
            int hole = symbol.getAddress();
            int save = ip;

            // fill in hole for goto jump
            ip = hole;
            genAddress(save);

            ip = save;

            statements();
        }
    }

    private static void procedureStat() {
        Symbol symbol = SymbolTable.lookup(currentToken.getTokenValue());
        if (symbol != null) {
            int address = symbol.getAddress();
            match("TK_A_PROC");
            match("TK_SEMI_COLON");
            // call procedure
            genOpCode(OP_CODE.JMP);
            genAddress(address);

            int restore = ip;

            // fill in return hole and restore ip
            ip = symbol.getReturnAddress();
            genAddress(restore);
            ip = restore;
        }
    }

    private static void goToStat() {
        match("TK_GOTO");
        Symbol symbol = SymbolTable.lookup(currentToken.getTokenValue());
        currentToken.setTokenType("TK_A_LABEL");
        match("TK_A_LABEL");
        genOpCode(OP_CODE.JMP);
        int hole = ip;
        genAddress(0);

        // hole for jump
        if (symbol != null){
            symbol.setAddress(hole);
        }

        match("TK_SEMI_COLON");

    }


    // for <variable name> := <initial value> to <final value> do <stat>
    private static void forStat() {
        match("TK_FOR");

        String varName = currentToken.getTokenValue();
        currentToken.setTokenType("TK_A_VAR");
        assignmentStat();

        int target = ip;


        Symbol symbol = SymbolTable.lookup(varName);
        if (symbol != null) {
            int address = symbol.getAddress();
            match("TK_TO");

            // Generate op code for x <= <upper bound>
            genOpCode(OP_CODE.PUSH);
            genAddress(address);
            genOpCode(OP_CODE.PUSHI);
            genAddress(Integer.valueOf(currentToken.getTokenValue()));

            genOpCode(OP_CODE.LEQ);
            match("TK_INTLIT");

            match("TK_DO");

            genOpCode(OP_CODE.JFALSE);
            int hole = ip;
            genAddress(0);

            match("TK_BEGIN");
            statements();
            match("TK_END");
            match("TK_SEMI_COLON");

            // Generate op code for x := x + 1;
            genOpCode(OP_CODE.PUSH);
            genAddress(address);
            genOpCode(OP_CODE.PUSHI);
            genAddress(1);
            genOpCode(OP_CODE.ADD);

            genOpCode(OP_CODE.POP);
            genAddress(address);


            genOpCode(OP_CODE.JMP);
            genAddress(target);

            int save = ip;
            ip = hole;
            genAddress(save);
            ip = save;
        }
    }

    // repeat <stat> until <cond>
    private static void repeatStat() {
        match("TK_REPEAT");
        int target = ip;
        statements();
        match("TK_UNTIL");
        C();
        genOpCode(OP_CODE.JFALSE);
        genAddress(target);
    }


    // while <cond> do <stat>
    private static void whileStat() {
        match("TK_WHILE");
        int target = ip;
        C();
        match("TK_DO");

        genOpCode(OP_CODE.JFALSE);
        int hole = ip;
        genAddress(0);

        match("TK_BEGIN");
        statements();
        match("TK_END");
        match("TK_SEMI_COLON");


        genOpCode(OP_CODE.JMP);
        genAddress(target);

        int save = ip;
        ip = hole;
        genAddress(save);
        ip = save;

    }

    // if <cond> then <stat>
    // if <cond> then <stat> else <stat>
    public static void ifStat(){
        match("TK_IF");
        C();
        match("TK_THEN");
        genOpCode(OP_CODE.JFALSE);
        int hole1 = ip;
        genAddress(0); // Holder value for the address
        statements();

        if(currentToken.getTokenType().equals("TK_ELSE")) {
            genOpCode(OP_CODE.JMP);
            int hole2 = ip;
            genAddress(0);
            int save = ip;
            ip = hole1;
            genAddress(save); // JFALSE to this else statement
            ip = save;
            hole1 = hole2;
            statements();
            match("TK_ELSE");
            statements();
        }

        int save = ip;
        ip = hole1;
        genAddress(save); // JFALSE to outside the if statement in if-then or JMP past the else statement in if-else
        ip = save;
    }

    /*
    case E of
        [<tags>: <statement>]^+
            [else <statement>]

    end

    <tags> -> <single tag> 10:
          <range> 3..9:
          <list> 3,5,7:
          <list of ranges> 1..2,30..40:
    */
    public static void caseStat() {
        match("TK_CASE");
        match("TK_OPEN_PARENTHESIS");
        Token eToken = currentToken;

        TYPE t1 = E();

        if (t1 == TYPE.R) {
            throw new Error("Invalid type of real for case E");
        }

        match("TK_CLOSE_PARENTHESIS");
        match("TK_OF");

        ArrayList<Integer> labelsArrayList = new ArrayList<>();

        while(currentToken.getTokenType().equals("TK_INTLIT") ||
                currentToken.getTokenType().equals("TK_CHARLIT") ||
                currentToken.getTokenType().equals("TK_BOOLLIT")) {

            TYPE t2 = E();
            emit("TK_EQUAL", t1, t2);
            match("TK_COLON");

            // hole for JFALSE to the next case label when the eql condition fails
            genOpCode(OP_CODE.JFALSE);
            int hole = ip;
            genAddress(0);
            statements();

            genOpCode(OP_CODE.JMP);
            labelsArrayList.add(ip);
            genAddress(0);

            // Fill JFALSE hole
            int save = ip;
            ip = hole;
            genAddress(save);

            ip = save;

            // PUSH the original eToken variable back to prepare for the next eql condition case label
            if (!currentToken.getTokenValue().equals("TK_END")){
                Symbol symbol = SymbolTable.lookup(eToken.getTokenValue());
                if (symbol != null) {
                    genOpCode(OP_CODE.PUSH);
                    genAddress(symbol.getAddress());
                }
            }
        }

        match("TK_END");
        match("TK_SEMI_COLON");

        int save = ip;

        // Fill all the labelHoles for JMP
        for (Integer labelHole: labelsArrayList) {
            ip = labelHole;
            genAddress(save);
        }

        ip = save;
    }

    public static void writeStat(){
        match("TK_WRITELN");
        match("TK_OPEN_PARENTHESIS");

        while (true) {
            Symbol symbol =  SymbolTable.lookup(currentToken.getTokenValue());
            TYPE t;

            if (symbol != null) {
                if (symbol.getDataType() == TYPE.A) {
                    // array
                    currentToken.setTokenType("TK_AN_ARRAY");
                    handleArrayAccess(symbol);

                    genOpCode(OP_CODE.GET);

                    t = symbol.getValueType();

                } else {
                    // variable
                    currentToken.setTokenType("TK_A_VAR");

                    t = symbol.getDataType();
                    genOpCode(OP_CODE.PUSH);
                    genAddress(symbol.getAddress());
                    match("TK_A_VAR");
                }
            } else {
                // literal
                t = getLitType(currentToken.getTokenType());
                assert t != null;
                switch (t) {
                    case R:
                        genOpCode(OP_CODE.PUSHF);
                        genAddress(Float.valueOf(currentToken.getTokenValue()));
                        break;
                    case I:
                        genOpCode(OP_CODE.PUSHI);
                        genAddress(Integer.valueOf(currentToken.getTokenValue()));
                        break;
                    case B:
                        genOpCode(OP_CODE.PUSHI);
                        if (currentToken.getTokenValue().equals("true")) {
                            genAddress(1);
                        } else {
                            genAddress(0);
                        }
                        break;
                    case C:
                        genOpCode(OP_CODE.PUSHI);
                        genAddress((int)(currentToken.getTokenValue().charAt(0)));
                        break;
                }

                match(currentToken.getTokenType());
            }

            assert t != null;
            switch (t) {
                case I:
                    genOpCode(OP_CODE.PRINT_INT);
                    break;
                case C:
                    genOpCode(OP_CODE.PRINT_CHAR);
                    break;
                case R:
                    genOpCode(OP_CODE.PRINT_REAL);
                    break;
                case B:
                    genOpCode(OP_CODE.PRINT_BOOL);
                    break;
                default:
                    throw new Error("Cannot write unknown type");

            }

            switch (currentToken.getTokenType()) {
                case "TK_COMMA":
                    match("TK_COMMA");
                    break;
                case "TK_CLOSE_PARENTHESIS":
                    match("TK_CLOSE_PARENTHESIS");
                    genOpCode(OP_CODE.PRINT_NEWLINE);
                    return;
                default:
                    throw new Error(String.format("Current token type (%s) is neither TK_COMMA nor TK_CLOSE_PARENTHESIS", currentToken.getTokenType()));
            }

        }
    }

    public static void assignmentStat() {
        Symbol symbol = SymbolTable.lookup(currentToken.getTokenValue());

        if (symbol != null) {
            TYPE lhsType = symbol.getDataType();
            int lhsAddress = symbol.getAddress();

            match("TK_A_VAR");

            match("TK_ASSIGNMENT");

            TYPE rhsType = E();
            if (lhsType == rhsType) {
                genOpCode(OP_CODE.POP);
                genAddress(lhsAddress);
            } else {
                throw new Error(String.format("LHS type (%s) is not equal to RHS type: (%s)", lhsType, rhsType));
            }
        }


    }


    private static void arrayAssignmentStat() {
        Symbol symbol = SymbolTable.lookup(currentToken.getTokenValue());
        if (symbol != null) {

            handleArrayAccess(symbol);

            match("TK_ASSIGNMENT");


            TYPE rhsType = E();
            // Emit OP_CODE.PUT
            if (symbol.getValueType() == rhsType) {
                genOpCode(OP_CODE.PUT);
            }

        }

    }

    private static void handleArrayAccess(Symbol symbol) {
        match("TK_AN_ARRAY");
        match("TK_OPEN_SQUARE_BRACKET");
        TYPE t;


        Symbol varSymbol = SymbolTable.lookup(currentToken.getTokenValue());
        if (varSymbol != null) {
            t = varSymbol.getDataType();


            if (t != symbol.getIndexType()) {
                throw new Error(String.format("Incompatible index type: (%s, %s)", t, symbol.getIndexType()));
            }

            currentToken.setTokenType("TK_A_VAR");
            genOpCode(OP_CODE.PUSH);
            genAddress(varSymbol.getAddress());
            match("TK_A_VAR");

            match("TK_CLOSE_SQUARE_BRACKET");

            genOpCode(OP_CODE.PUSHI);

            switch (t) {
                case I:

                    int i1 = (int) symbol.getLow();
                    int i2 = (int) symbol.getHigh();

                    genAddress(i1);
                    genOpCode(OP_CODE.XCHG);
                    genOpCode(OP_CODE.SUB);

                    // push element size
                    genOpCode(OP_CODE.PUSHI);
                    genAddress(4);

                    genOpCode(OP_CODE.MULT);

                    genOpCode(OP_CODE.PUSHI);
                    genAddress(symbol.getAddress());

                    genOpCode(OP_CODE.ADD);

                    break;
                case C:
                    char c1 = (char) symbol.getLow();
                    char c2 = (char) symbol.getHigh();

                    genAddress(c1);
                    genOpCode(OP_CODE.XCHG);
                    genOpCode(OP_CODE.SUB);

                    genOpCode(OP_CODE.PUSHI);
                    genAddress(symbol.getAddress());

                    genOpCode(OP_CODE.ADD);

                    break;
            }
        } else {


            String index = currentToken.getTokenValue();
            t = E();

            if (t != symbol.getIndexType()) {
                throw new Error(String.format("Incompatible index type: (%s, %s)", t, symbol.getIndexType()));
            }

            match("TK_CLOSE_SQUARE_BRACKET");

            genOpCode(OP_CODE.PUSHI);

            switch (t) {
                case I:

                    int i1 = (int) symbol.getLow();
                    int i2 = (int) symbol.getHigh();

                    // range check:
                    if (Integer.valueOf(index) < i1 || Integer.valueOf(index) > i2) {
                        throw new Error(String.format("Index %d is not within range %d to %d",
                                Integer.valueOf(index), i1, i2));
                    }

                    genAddress(i1);
                    genOpCode(OP_CODE.XCHG);
                    genOpCode(OP_CODE.SUB);

                    // push element size
                    genOpCode(OP_CODE.PUSHI);
                    genAddress(4);

                    genOpCode(OP_CODE.MULT);

                    genOpCode(OP_CODE.PUSHI);
                    genAddress(symbol.getAddress());

                    genOpCode(OP_CODE.ADD);

                    break;
                case C:
                    char c1 = (char) symbol.getLow();
                    char c2 = (char) symbol.getHigh();

                    // range check
                    if (index.toCharArray()[0] < c1 || index.toCharArray()[0] > c2) {
                        throw new Error(String.format("Index %c is not within range %c to %c",
                                index.toCharArray()[0], c1, c2));
                    }

                    genAddress(c1);
                    genOpCode(OP_CODE.XCHG);
                    genOpCode(OP_CODE.SUB);

                    genOpCode(OP_CODE.PUSHI);
                    genAddress(symbol.getAddress());

                    genOpCode(OP_CODE.ADD);

                    break;
            }

        }
    }

    /*
    Condition
    C -> EC'
    C' -> < EC' | > EC' | <= EC' | >= EC' | = EC' | <> EC' | epsilon
     */
    public static TYPE C(){
        TYPE e1 = E();
        while (currentToken.getTokenType().equals("TK_LESS_THAN") ||
                currentToken.getTokenType().equals("TK_GREATER_THAN") ||
                currentToken.getTokenType().equals("TK_LESS_THAN_EQUAL") ||
                currentToken.getTokenType().equals("TK_GREATER_THAN_EQUAL") ||
                currentToken.getTokenType().equals("TK_EQUAL") ||
                currentToken.getTokenType().equals("TK_NOT_EQUAL")) {
            String pred = currentToken.getTokenType();
            match(pred);
            TYPE e2 = T();

            e1 = emit(pred, e1, e2);
        }

        return e1;
    }


    /*
    Expression
    E -> TE'
    E' -> +TE' | -TE' | epsilon
     */
    public static TYPE E(){
        TYPE t1 = T();
        while (currentToken.getTokenType().equals("TK_PLUS") || currentToken.getTokenType().equals("TK_MINUS")) {
            String op = currentToken.getTokenType();
            match(op);
            TYPE t2 = T();

            t1 = emit(op, t1, t2);
        }

        return t1;
    }

    /*
    Term
    T -> FT'
    T' ->  *FT' | /FT' | epsilon
     */
    public static TYPE T() {
        TYPE f1 = F();
        while (currentToken.getTokenType().equals("TK_MULTIPLY") ||
                currentToken.getTokenType().equals("TK_DIVIDE") ||
                currentToken.getTokenType().equals("TK_DIV")) {
            String op = currentToken.getTokenType();
            match(op);
            TYPE f2 = F();

            f1 = emit(op, f1, f2);
        }
        return f1;
    }


    /*
    Factor
    F -> id | lit | (E) | not F | +F | -F
     */
    public static TYPE F() {
        switch (currentToken.getTokenType()) {
            case "TK_IDENTIFIER":
                Symbol symbol = SymbolTable.lookup(currentToken.getTokenValue());
                if (symbol != null) {
                    if (symbol.getTokenType().equals("TK_A_VAR")) {
                        // variable
                        currentToken.setTokenType("TK_A_VAR");

                        genOpCode(OP_CODE.PUSH);
                        genAddress(symbol.getAddress());

                        match("TK_A_VAR");
                        return symbol.getDataType();
                    } else if (symbol.getTokenType().equals("TK_AN_ARRAY")) {
                        currentToken.setTokenType("TK_AN_ARRAY");

                        handleArrayAccess(symbol);
                        genOpCode(OP_CODE.GET);

                        return symbol.getValueType();
                    }
                } else {
                    throw new Error(String.format("Symbol not found (%s)", currentToken.getTokenValue()));
                }
            case "TK_INTLIT":
                genOpCode(OP_CODE.PUSHI);
                genAddress(Integer.valueOf(currentToken.getTokenValue()));

                match("TK_INTLIT");
                return TYPE.I;
            case "TK_FLOATLIT":
                genOpCode(OP_CODE.PUSHF);
                genAddress(Float.valueOf(currentToken.getTokenValue()));

                match("TK_FLOATLIT");
                return TYPE.R;
            case "TK_BOOLLIT":
                genOpCode(OP_CODE.PUSHI);
                genAddress(Boolean.valueOf(currentToken.getTokenValue()) ? 1 : 0);

                match("TK_BOOLLIT");
                return TYPE.B;
            case "TK_CHARLIT":
                genOpCode(OP_CODE.PUSHI);
                genAddress(currentToken.getTokenValue().charAt(0));

                match("TK_CHARLIT");
                return TYPE.C;
            case "TK_STRLIT":
                for (char c: currentToken.getTokenType().toCharArray()) {
                    genOpCode(OP_CODE.PUSHI);
                    genAddress(c);
                }

                match("TK_STRLIT");
                return TYPE.S;
            case "TK_NOT":
                match("TK_NOT");
                return F();
            case "TK_OPEN_PARENTHESIS":
                match("TK_OPEN_PARENTHESIS");
                TYPE t = E();
                match("TK_CLOSE_PARENTHESIS");
                return t;
            default:
                throw new Error("Unknown data type");
        }

    }


    public static TYPE emit(String op, TYPE t1, TYPE t2){
        switch (op) {
            case "TK_PLUS":
                if (t1 == TYPE.I && t2 == TYPE.I) {
                    genOpCode(OP_CODE.ADD);
                    return TYPE.I;
                } else if (t1 == TYPE.I && t2 == TYPE.R) {
                    genOpCode(OP_CODE.XCHG);
                    genOpCode(OP_CODE.CVR);
                    genOpCode(OP_CODE.FADD);
                    return TYPE.R;
                } else if (t1 == TYPE.R && t2 == TYPE.I) {
                    genOpCode(OP_CODE.CVR);
                    genOpCode(OP_CODE.FADD);
                    return TYPE.R;
                } else if (t1 == TYPE.R && t2 == TYPE.R) {
                    genOpCode(OP_CODE.FADD);
                    return TYPE.R;
                }
            case "TK_MINUS":
                if (t1 == TYPE.I && t2 == TYPE.I) {
                    genOpCode(OP_CODE.SUB);
                    return TYPE.I;
                } else if (t1 == TYPE.I && t2 == TYPE.R) {
                    genOpCode(OP_CODE.XCHG);
                    genOpCode(OP_CODE.CVR);
                    genOpCode(OP_CODE.FSUB);
                    return TYPE.R;
                } else if (t1 == TYPE.R && t2 == TYPE.I) {
                    genOpCode(OP_CODE.CVR);
                    genOpCode(OP_CODE.FSUB);
                    return TYPE.R;
                } else if (t1 == TYPE.R && t2 == TYPE.R) {
                    genOpCode(OP_CODE.FSUB);
                    return TYPE.R;
                }
            case "TK_MULTIPLY":
                if (t1 == TYPE.I && t2 == TYPE.I) {
                    genOpCode(OP_CODE.MULT);
                    return TYPE.I;
                } else if (t1 == TYPE.I && t2 == TYPE.R) {
                    genOpCode(OP_CODE.XCHG);
                    genOpCode(OP_CODE.CVR);
                    genOpCode(OP_CODE.FMULT);
                    return TYPE.R;
                } else if (t1 == TYPE.R && t2 == TYPE.I) {
                    genOpCode(OP_CODE.CVR);
                    genOpCode(OP_CODE.FMULT);
                    return TYPE.R;
                } else if (t1 == TYPE.R && t2 == TYPE.R) {
                    genOpCode(OP_CODE.FMULT);
                    return TYPE.R;
                }
            case "TK_DIVIDE":
                if (t1 == TYPE.I && t2 == TYPE.I) {
                    genOpCode(OP_CODE.CVR);
                    genOpCode(OP_CODE.XCHG);
                    genOpCode(OP_CODE.CVR);
                    genOpCode(OP_CODE.XCHG);
                    genOpCode(OP_CODE.FDIV);
                    return TYPE.R;
                } else if (t1 == TYPE.I && t2 == TYPE.R) {
                    genOpCode(OP_CODE.XCHG);
                    genOpCode(OP_CODE.CVR);
                    genOpCode(OP_CODE.FDIV);
                    return TYPE.R;
                } else if (t1 == TYPE.R && t2 == TYPE.I) {
                    genOpCode(OP_CODE.CVR);
                    genOpCode(OP_CODE.FDIV);
                    return TYPE.R;
                } else if (t1 == TYPE.R && t2 == TYPE.R) {
                    genOpCode(OP_CODE.FDIV);
                    return TYPE.R;
                }
            case "TK_DIV":
                if (t1 == TYPE.I && t2 == TYPE.I) {
                    genOpCode(OP_CODE.DIV);
                    return TYPE.I;
                }
            case "TK_LESS_THAN":
                return emitBool(OP_CODE.LSS, t1, t2);
            case "TK_GREATER_THAN":
                return emitBool(OP_CODE.GTR, t1, t2);
            case "TK_LESS_THAN_EQUAL":
                return emitBool(OP_CODE.LEQ, t1, t2);
            case "TK_GREATER_THAN_EQUAL":
                return emitBool(OP_CODE.GEQ, t1, t2);
            case "TK_EQUAL":
                return emitBool(OP_CODE.EQL, t1, t2);
            case "TK_NOT_EQUAL":
                return emitBool(OP_CODE.NEQL, t1, t2);
        }

        return null;
    }

    public static TYPE emitBool(OP_CODE pred, TYPE t1, TYPE t2) {
        if (t1 == t2) {
            genOpCode(pred);
            return TYPE.B;
        } else if (t1 == TYPE.I && t2 == TYPE.R) {
            genOpCode(OP_CODE.XCHG);
            genOpCode(OP_CODE.CVR);
            genOpCode(pred);
            return TYPE.B;
        } else if (t1 == TYPE.R && t2 == TYPE.I) {
            genOpCode(OP_CODE.CVR);
            genOpCode(pred);
            return TYPE.B;
        }

        return null;
    }

    public static void genOpCode(OP_CODE b){
//        System.out.println(String.format("OP_CODE: %s", b));
        byteArray[ip++] = (byte)(b.ordinal());
    }

    public static void genAddress(int a){
//        System.out.println(String.format("ADDRESS_VALUE: %s", a));
        byte[] intBytes = ByteBuffer.allocate(ADDRESS_SIZE).putInt(a).array();

        for (byte b: intBytes) {
            byteArray[ip++] = b;
        }
    }

    public static void genAddress(float a){
//        System.out.println(String.format("ADDRESS_VALUE: %s", a));
        byte[] intBytes = ByteBuffer.allocate(ADDRESS_SIZE).putFloat(a).array();

        for (byte b: intBytes) {
            byteArray[ip++] = b;
        }
    }

    public static void getToken() {
        if (it.hasNext()) {
            currentToken =  it.next();
        }
    }

    public static void match(String tokenType) {
        if (!tokenType.equals(currentToken.getTokenType())) {
            throw new Error(String.format("Token type (%s) does not match current token type (%s)", tokenType, currentToken.getTokenType()));
        } else {
//            System.out.println(String.format("matched: %s", currentToken.getTokenType()));
            getToken();
        }
    }

    public static TYPE getLitType(String tokenType) {
        switch (tokenType) {
            case "TK_INTLIT":
                return TYPE.I;
            case "TK_FLOATLIT":
                return TYPE.R;
            case "TK_CHARLIT":
                return TYPE.C;
            case "TK_BOOLLIT":
                return TYPE.B;
            default:
                return null;
        }
    }

    public static void setTokenArrayListIterator(ArrayList<Token> tokenArrayList) {
        it = tokenArrayList.iterator();
    }
}
