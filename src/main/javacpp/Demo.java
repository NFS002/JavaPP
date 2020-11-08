package javacpp;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class Demo {



    public static void main(String[] args) throws IOException {


        Java8Lexer java8Lexer = new Java8Lexer(CharStreams.fromFileName("src/resources/SimpleClass.java"));

        CommonTokenStream tokens = new CommonTokenStream(java8Lexer);

        TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

        Java8Parser parser = new Java8Parser(tokens);

        ParseTree tree = parser.compilationUnit();

        ParseTreeWalker walker = new ParseTreeWalker();

        MethodReturnTypeListener resultListener = new MethodReturnTypeListener(rewriter);

        ReturnListener returnListener = new ReturnListener(rewriter);

        TupleAssignmentListener tupleAssignmentListener = new TupleAssignmentListener(rewriter, resultListener.getFunctionReturnTypes());

        walker.walk(resultListener, tree);

        walker.walk(returnListener, tree);

        walker.walk(tupleAssignmentListener, tree);

        PrintWriter writer = new PrintWriter("src/resources/SimpleClass.java");

        writer.println(rewriter.getText());

        writer.close();

    }

    static class Util {
        public static String parseTreeContextToString(ParserRuleContext ctx) {
            StringBuilder stringBuilder = new StringBuilder();
            for (ParseTree tree : ctx.children) {
                stringBuilder.append(parseTreeToString(tree));

            }
            return stringBuilder.toString();
        }

        private static String parseTreeToString(ParseTree tree) {
            int nChild = tree.getChildCount();
            StringBuilder stringBuilder = new StringBuilder();
            if (tree.getChildCount() > 0 ) {
                for (int i = 0; i < nChild; i++){
                    stringBuilder.append(parseTreeToString(tree.getChild(i)));
                }
            }
            else stringBuilder.append(tree.getText() + " ");
            return stringBuilder.toString();
        }
    }

    static class TupleAssignmentListener extends Java8BaseListener {


        private final TokenStreamRewriter rewriter;

        private final HashMap<String, String> functionReturnTypes;


        TupleAssignmentListener(TokenStreamRewriter rewriter, HashMap<String, String> functionReturnTypes) {
            this.rewriter = rewriter;
            this.functionReturnTypes = functionReturnTypes;
        }

        @Override
        public void enterTupleAssignment(Java8Parser.TupleAssignmentContext ctx)  {
            int lineNo = ctx.start.getLine();
            List<ParseTree> trees = ctx.children;
            assert trees.size() == 7;
            String type0 = trees.get(0).getText();
            String var0 = trees.get(1).getText();
            String type1 = trees.get(3).getText();
            String var1 = trees.get(4).getText();
            String fullFunctionCall = trees.get(6).getText();
            String fullFunctionName = fullFunctionCall.replaceAll("\\(.*\\)", "");
            String[] functionNameParts = fullFunctionName.split("\\.");
            String functionIdent = functionNameParts[functionNameParts.length - 1];
            String returnType = this.functionReturnTypes.get(functionIdent);
            if (returnType == null) {
                throw new RuntimeException(
                        new ParseException(
                                String.format("Method %s does not have a tuple return type or was not found (line %d)", fullFunctionCall, lineNo), ctx.start.getTokenIndex()
                        )
                );
            }
            String randomIdent = getRandomIdentifier();
            String replacement0 = String.format("%s %s = %s;", returnType, randomIdent, fullFunctionCall);
            String replacement1 = String.format("%s %s = %s.getFirst();",type0,var0, randomIdent);
            String replacement2 = String.format("%s %s = %s.getSecond()", type1, var1, randomIdent);
            String fullReplacement = String.format("%s\n\t\t%s\n\t\t%s", replacement0, replacement1, replacement2);
            this.rewriter.replace(ctx.start, ctx.stop, fullReplacement);
        }

        private String getRandomIdentifier() {
            return "javacpp" + UUID.randomUUID().toString().replaceAll("[^A-Za-z0-9]", "");
        }
    }

    static class ReturnListener extends Java8BaseListener {

        private final TokenStreamRewriter rewriter;

        public ReturnListener(TokenStreamRewriter rewriter) {
            this.rewriter = rewriter;
        }


        @Override
        public void enterReturnStatement(Java8Parser.ReturnStatementContext ctx) {
            String stmt = Util.parseTreeContextToString(ctx);
            String[] stmts = stmt.split(",");
            if (stmts.length == 2) {
                stmts[0] = stmts[0].replaceAll("return", "").trim();
                stmts[1] = stmts[1].replaceAll(";", "").trim();
                String replacement = String.format("return new com.javacpp.util.GenericTuple<>(%s, %s);", stmts[0], stmts[1]);
                this.rewriter.replace(ctx.start, ctx.stop, replacement);
            }
        }

    }

    static class MethodReturnTypeListener extends Java8BaseListener {

        private final TokenStreamRewriter rewriter;

        private final HashMap<String, String> functionReturnTypes;

        private final HashMap<String, String> p2rMap = new HashMap<>();

        public MethodReturnTypeListener(TokenStreamRewriter rewriter) {
            this.rewriter = rewriter;
            this.functionReturnTypes = new HashMap<>();
            this.p2rMap.put("int", "Integer");
            this.p2rMap.put("boolean", "Boolean");
            this.p2rMap.put("byte", "Byte");
            this.p2rMap.put("long", "Long");
            this.p2rMap.put("double", "Double");
            this.p2rMap.put("char", "Char");
            this.p2rMap.put("float", "Float");
        }

        public HashMap<String, String> getFunctionReturnTypes() {
            return functionReturnTypes;
        }

        private boolean isPrimitive(String type) {
            return this.p2rMap.containsKey(type);
        }

        @Override
        public void enterResult(Java8Parser.ResultContext ctx) {
            RuleContext funcHeader = ctx.parent;
            String funcName = funcHeader.getChild(1).getText().replaceAll("\\(.*\\)", "");
            if (ctx.children.size() == 3 && ctx.getText().contains(",")) {
                String pt0 = ctx.children.get(0).getText();
                String pt1 = ctx.children.get(2).getText();
                if (this.isPrimitive(pt0))
                    pt0 = this.p2rMap.get(pt0);
                if (this.isPrimitive(pt1))
                    pt1 = this.p2rMap.get(pt1);
                String types = String.format("%s, %s", pt0, pt1);
                String replacement = String.format("com.javacpp.util.GenericTuple<%s>", types);
                this.functionReturnTypes.put(funcName, replacement);
                this.rewriter.replace(ctx.start, ctx.stop, replacement);
            }

        }
    }
}
