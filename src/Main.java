import ast.*;

import java.io.*;

public class Main {
    public static void main(String[] args) {
        try {
            var inputMethod = args[0];
            var action = args[1];
            var filename = args[args.length - 2];
            var outfilename = args[args.length - 1];

            Program prog;

            if (inputMethod.equals("parse")) {
                Parser p = new Parser(new Lexer(new FileReader(filename)));
                prog = (Program) (p.parse().value);
                AstXMLSerializer xmlSerializer = new AstXMLSerializer();
                xmlSerializer.serialize(prog, outfilename);
            } else if (inputMethod.equals("unmarshal")) {
                AstXMLSerializer xmlSerializer = new AstXMLSerializer();
                prog = xmlSerializer.deserialize(new File(filename));
            } else {
                throw new UnsupportedOperationException("unknown input method " + inputMethod);
            }

            var outFile = new PrintWriter(outfilename);
            try {

                if (action.equals("marshal")) {
                    AstXMLSerializer xmlSerializer = new AstXMLSerializer();
                    xmlSerializer.serialize(prog, outfilename);
                } else if (action.equals("print")) {
                    AstPrintVisitor astPrinter = new AstPrintVisitor();
                    astPrinter.visit(prog);
                    outFile.write(astPrinter.getString());

                } else if (action.equals("semantic")) {
                    SemanticChecksVisitor v = new SemanticChecksVisitor();
                    prog.accept(v);
                    PrintWriter out = new PrintWriter(outfilename);
                    String output = v.isLegalProgram() ? "OK" : "ERROR";
                    out.print(output);
                    out.close();

                } else if (action.equals("compile")) {
                    LLVMVisitor v = new LLVMVisitor(prog);
                    prog.accept(v);
                    PrintWriter out = new PrintWriter(outfilename);
                    String output = v.getLLVMProgram();
                    out.print(output);
                    out.close();
                } else if (action.equals("rename")) {
                    var type = args[2];
                    var originalName = args[3];
                    var originalLine = args[4];
                    var newName = args[5];

                    boolean isMethod;
                    if (type.equals("var")) {
                        isMethod = false;
                        VariableRenameVisitor v = new VariableRenameVisitor(originalName, newName, Integer.parseInt(originalLine));
                        prog.accept(v);
                        AstXMLSerializer xmlSerializer = new AstXMLSerializer();
                        xmlSerializer.serialize(prog, outfilename);
                    } else if (type.equals("method")) {
                        isMethod = true;
                        MethodRenameVisitor v = new MethodRenameVisitor(originalName, newName, Integer.parseInt(originalLine));
                        prog.accept(v);
                        AstXMLSerializer xmlSerializer = new AstXMLSerializer();
                        xmlSerializer.serialize(prog, outfilename);
                    } else {
                        throw new IllegalArgumentException("unknown rename type " + type);
                    }
                } else {
                    throw new IllegalArgumentException("unknown command line action " + action);
                }
            } finally {
                outFile.flush();
                outFile.close();
            }

        } catch (FileNotFoundException e) {
            System.out.println("Error reading file: " + e);
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("General error: " + e);
            e.printStackTrace();
        }
    }
}
