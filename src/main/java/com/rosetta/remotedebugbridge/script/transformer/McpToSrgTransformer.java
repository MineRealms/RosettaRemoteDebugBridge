/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.rain.repack.javaparser.JavaParser
 *  net.rain.repack.javaparser.ParseResult
 *  net.rain.repack.javaparser.ast.CompilationUnit
 *  net.rain.repack.javaparser.ast.ImportDeclaration
 *  net.rain.repack.javaparser.ast.Node
 *  net.rain.repack.javaparser.ast.NodeList
 *  net.rain.repack.javaparser.ast.PackageDeclaration
 *  net.rain.repack.javaparser.ast.body.ClassOrInterfaceDeclaration
 *  net.rain.repack.javaparser.ast.body.FieldDeclaration
 *  net.rain.repack.javaparser.ast.body.MethodDeclaration
 *  net.rain.repack.javaparser.ast.body.Parameter
 *  net.rain.repack.javaparser.ast.body.VariableDeclarator
 *  net.rain.repack.javaparser.ast.expr.BooleanLiteralExpr
 *  net.rain.repack.javaparser.ast.expr.CastExpr
 *  net.rain.repack.javaparser.ast.expr.CharLiteralExpr
 *  net.rain.repack.javaparser.ast.expr.ClassExpr
 *  net.rain.repack.javaparser.ast.expr.DoubleLiteralExpr
 *  net.rain.repack.javaparser.ast.expr.Expression
 *  net.rain.repack.javaparser.ast.expr.FieldAccessExpr
 *  net.rain.repack.javaparser.ast.expr.IntegerLiteralExpr
 *  net.rain.repack.javaparser.ast.expr.LambdaExpr
 *  net.rain.repack.javaparser.ast.expr.LongLiteralExpr
 *  net.rain.repack.javaparser.ast.expr.MethodCallExpr
 *  net.rain.repack.javaparser.ast.expr.NameExpr
 *  net.rain.repack.javaparser.ast.expr.NullLiteralExpr
 *  net.rain.repack.javaparser.ast.expr.ObjectCreationExpr
 *  net.rain.repack.javaparser.ast.expr.StringLiteralExpr
 *  net.rain.repack.javaparser.ast.expr.ThisExpr
 *  net.rain.repack.javaparser.ast.expr.VariableDeclarationExpr
 *  net.rain.repack.javaparser.ast.stmt.BlockStmt
 *  net.rain.repack.javaparser.ast.stmt.CatchClause
 *  net.rain.repack.javaparser.ast.stmt.Statement
 *  net.rain.repack.javaparser.ast.stmt.TryStmt
 *  net.rain.repack.javaparser.ast.visitor.GenericVisitor
 *  net.rain.repack.javaparser.ast.visitor.ModifierVisitor
 *  net.rain.repack.javaparser.ast.visitor.Visitable
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package com.rosetta.remotedebugbridge.script.transformer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.rosetta.remotedebugbridge.script.utils.MinecraftHelper;
import net.rain.repack.javaparser.JavaParser;
import net.rain.repack.javaparser.ParseResult;
import net.rain.repack.javaparser.ast.CompilationUnit;
import net.rain.repack.javaparser.ast.ImportDeclaration;
import net.rain.repack.javaparser.ast.Node;
import net.rain.repack.javaparser.ast.NodeList;
import net.rain.repack.javaparser.ast.PackageDeclaration;
import net.rain.repack.javaparser.ast.body.ClassOrInterfaceDeclaration;
import net.rain.repack.javaparser.ast.body.FieldDeclaration;
import net.rain.repack.javaparser.ast.body.MethodDeclaration;
import net.rain.repack.javaparser.ast.body.Parameter;
import net.rain.repack.javaparser.ast.body.VariableDeclarator;
import net.rain.repack.javaparser.ast.expr.BooleanLiteralExpr;
import net.rain.repack.javaparser.ast.expr.CastExpr;
import net.rain.repack.javaparser.ast.expr.CharLiteralExpr;
import net.rain.repack.javaparser.ast.expr.ClassExpr;
import net.rain.repack.javaparser.ast.expr.DoubleLiteralExpr;
import net.rain.repack.javaparser.ast.expr.Expression;
import net.rain.repack.javaparser.ast.expr.FieldAccessExpr;
import net.rain.repack.javaparser.ast.expr.IntegerLiteralExpr;
import net.rain.repack.javaparser.ast.expr.LambdaExpr;
import net.rain.repack.javaparser.ast.expr.LongLiteralExpr;
import net.rain.repack.javaparser.ast.expr.MethodCallExpr;
import net.rain.repack.javaparser.ast.expr.NameExpr;
import net.rain.repack.javaparser.ast.expr.NullLiteralExpr;
import net.rain.repack.javaparser.ast.expr.ObjectCreationExpr;
import net.rain.repack.javaparser.ast.expr.StringLiteralExpr;
import net.rain.repack.javaparser.ast.expr.ThisExpr;
import net.rain.repack.javaparser.ast.expr.VariableDeclarationExpr;
import net.rain.repack.javaparser.ast.stmt.BlockStmt;
import net.rain.repack.javaparser.ast.stmt.CatchClause;
import net.rain.repack.javaparser.ast.stmt.Statement;
import net.rain.repack.javaparser.ast.stmt.TryStmt;
import net.rain.repack.javaparser.ast.visitor.GenericVisitor;
import net.rain.repack.javaparser.ast.visitor.ModifierVisitor;
import net.rain.repack.javaparser.ast.visitor.Visitable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class McpToSrgTransformer {
    private static final Logger LOGGER = LogManager.getLogger();
    private final JavaParser javaParser = new JavaParser();

    public String transformFile(Path sourceFile) throws IOException {
        return this.transformSource(Files.readString(sourceFile), sourceFile.toString());
    }

    public String transformSource(String sourceCode, String fileName) {
        try {
            ParseResult<CompilationUnit> parseResult = this.javaParser.parse(sourceCode);
            if (!parseResult.isSuccessful()) {
                LOGGER.error("Failed to parse {}: {}", (Object)fileName, (Object)parseResult.getProblems());
                return sourceCode;
            }
            CompilationUnit cu = parseResult.getResult().orElse(null);
            if (cu == null) {
                LOGGER.error("Failed to get compilation unit for {}", (Object)fileName);
                return sourceCode;
            }
            McpToSrgVisitor visitor = new McpToSrgVisitor();
            cu.accept((GenericVisitor)visitor, null);
            if (visitor.getTransformCount() > 0) {
                LOGGER.info("Transformed {} MCP names to SRG in {}", (Object)visitor.getTransformCount(), (Object)fileName);
            }
            return cu.toString();
        }
        catch (Exception e) {
            LOGGER.error("Error transforming {}: {}", (Object)fileName, (Object)e.getMessage(), (Object)e);
            return sourceCode;
        }
    }

    private static class McpToSrgVisitor
    extends ModifierVisitor<Void> {
        private int transformCount = 0;
        private static final Map<String, String> KNOWN_RETURN_TYPES = new HashMap<String, String>();
        private static final Map<String, String> MC_SUPER_CLASS;
        private static final List<String> BRIGADIER_BUILDER_CLASSES;
        private static final String[] TRANSFORM_PACKAGES;
        private static final String SENTINEL = "__SENTINEL__";

        private McpToSrgVisitor() {
        }

        private boolean shouldTransform(String className) {
            if (className == null || className.isEmpty()) {
                return false;
            }
            for (String p : TRANSFORM_PACKAGES) {
                if (!className.startsWith(p)) continue;
                return true;
            }
            return false;
        }

        public int getTransformCount() {
            return this.transformCount;
        }

        private String findSrgMethodInHierarchy(String className, String methodName) {
            return this.findSrgMethodInHierarchy(className, methodName, null);
        }

        private String findSrgMethodInHierarchy(String className, String methodName, String descriptor) {
            String current = className;
            HashSet<String> visited = new HashSet<String>();
            while (current != null && !visited.contains(current)) {
                String srg;
                visited.add(current);
                if (descriptor != null && (srg = MinecraftHelper.findSrgMethodName(current, methodName, descriptor)) != null) {
                    return srg;
                }
                srg = MinecraftHelper.findSrgMethodName(current, methodName);
                if (srg != null) {
                    return srg;
                }
                String parent = MC_SUPER_CLASS.getOrDefault(current, SENTINEL);
                if (!(parent != SENTINEL || (parent = MinecraftHelper.findSuperClassName(current)) == null || parent.equals("java.lang.Object") || parent.equals("java/lang/Object") || parent.equals("Obejct"))) {
                    parent = parent.replace('/', '.');
                }
                current = parent;
            }
            return null;
        }

        private String findSrgFieldInHierarchy(String className, String fieldName) {
            String current = className;
            HashSet<String> visited = new HashSet<String>();
            while (current != null && !visited.contains(current)) {
                visited.add(current);
                String srg = MinecraftHelper.findSrgFieldName(current, fieldName);
                if (srg != null) {
                    return srg;
                }
                String parent = MC_SUPER_CLASS.getOrDefault(current, SENTINEL);
                if (!(parent != SENTINEL || (parent = MinecraftHelper.findSuperClassName(current)) == null || parent.equals("java.lang.Object") || parent.equals("java/lang/Object") || parent.equals("Obejct"))) {
                    parent = parent.replace('/', '.');
                }
                current = parent;
            }
            return null;
        }

        public Visitable visit(FieldAccessExpr n, Void arg) {
            String srg;
            String fieldName = n.getNameAsString();
            String className = n.getScope() != null ? this.resolveClassName(n.getScope()) : null;
            super.visit(n, arg);
            if (className != null && this.shouldTransform(className) && (srg = this.findSrgFieldInHierarchy(className, fieldName)) != null && !srg.equals(fieldName)) {
                LOGGER.info("Transforming field: {}.{} -> {}", (Object)className, (Object)fieldName, (Object)srg);
                n.setName(srg);
                ++this.transformCount;
            }
            return n;
        }

        public Visitable visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            String className = n.getScope().isPresent() ? this.resolveClassName((Expression)n.getScope().get()) : this.getEnclosingClassName((Node)n);
            super.visit(n, arg);
            if (className != null && this.shouldTransform(className)) {
                String descriptor = this.buildMethodDescriptor(n);
                String srg = this.findSrgMethodInHierarchy(className, methodName, descriptor);
                if (srg == null) {
                    srg = this.findSrgMethodInHierarchy(className, methodName);
                }
                if (srg != null && !srg.equals(methodName)) {
                    LOGGER.info("Transforming method: {}.{}() -> {}()", (Object)className, (Object)methodName, (Object)srg);
                    n.setName(srg);
                    ++this.transformCount;
                }
            }
            return n;
        }

        public Visitable visit(NameExpr n, Void arg) {
            String srg;
            super.visit(n, arg);
            String fieldName = n.getNameAsString();
            Optional parent = n.getParentNode();
            String className = null;
            while (parent.isPresent()) {
                Node node = (Node)parent.get();
                if (node instanceof ClassOrInterfaceDeclaration) {
                    className = this.getFullyQualifiedName((ClassOrInterfaceDeclaration)node);
                    break;
                }
                parent = node.getParentNode();
            }
            if (className != null && this.shouldTransform(className) && (srg = this.findSrgFieldInHierarchy(className, fieldName)) != null && !srg.equals(fieldName)) {
                LOGGER.info("Transforming field reference: {} -> {} in {}", (Object)fieldName, (Object)srg, (Object)className);
                n.setName(srg);
                ++this.transformCount;
            }
            return n;
        }

        private String resolveClassName(Expression expr) {
            if (expr instanceof ObjectCreationExpr) {
                return this.resolveFullClassName(((ObjectCreationExpr)expr).getType().asString(), (Node)expr);
            }
            if (expr instanceof NameExpr) {
                NameExpr ne = (NameExpr)expr;
                String varType = this.resolveVariableType(ne);
                if (varType != null) {
                    return varType;
                }
                String fromMap = MinecraftHelper.findFullClassName(ne.getNameAsString());
                if (fromMap != null) {
                    return fromMap;
                }
                return this.resolveFullClassName(ne.getNameAsString(), (Node)ne);
            }
            if (expr instanceof ThisExpr) {
                return this.getEnclosingClassName((Node)expr);
            }
            if (expr instanceof FieldAccessExpr) {
                FieldAccessExpr fa = (FieldAccessExpr)expr;
                String qn = this.reconstructQualifiedName((Expression)fa);
                if (qn != null) {
                    String fromMap = MinecraftHelper.findFullClassName(qn);
                    if (fromMap != null) {
                        return fromMap;
                    }
                    if (this.shouldTransform(qn)) {
                        return qn;
                    }
                }
                return this.resolveFieldType(fa);
            }
            if (expr instanceof MethodCallExpr) {
                return this.resolveMethodReturnType((MethodCallExpr)expr);
            }
            if (expr instanceof ClassExpr) {
                return this.resolveFullClassName(((ClassExpr)expr).getType().asString(), (Node)expr);
            }
            if (expr instanceof CastExpr) {
                return this.resolveFullClassName(((CastExpr)expr).getType().asString(), (Node)expr);
            }
            return null;
        }

        private String resolveMethodReturnType(MethodCallExpr call) {
            String rt;
            String known;
            Optional scopeOpt = call.getScope();
            String className = scopeOpt.isPresent() ? this.resolveClassName((Expression)scopeOpt.get()) : this.getEnclosingClassName((Node)call);
            String methodName = call.getNameAsString();
            if (className != null && (known = KNOWN_RETURN_TYPES.get(className + "|" + methodName)) != null) {
                return known;
            }
            if (className != null && (rt = this.mcFindReturnTypeInHierarchy(className, methodName)) != null) {
                return rt.replace('/', '.');
            }
            List<String> candidates = className != null ? Collections.singletonList(className) : BRIGADIER_BUILDER_CLASSES;
            int argCount = call.getArguments().size();
            for (String candidate : candidates) {
                try {
                    Class<?> clazz = Class.forName(candidate);
                    String result = this.reflectMethodReturnType(clazz, methodName, argCount);
                    if (result == null) continue;
                    return result;
                }
                catch (ClassNotFoundException classNotFoundException) {
                }
            }
            return null;
        }

        private String mcFindReturnTypeInHierarchy(String className, String methodName) {
            String current = className;
            HashSet<String> visited = new HashSet<String>();
            while (current != null && !visited.contains(current)) {
                visited.add(current);
                String rt = MinecraftHelper.findMethodReturnType(current, methodName);
                if (rt != null) {
                    return rt;
                }
                String alt = current.contains(".") ? current.replace('.', '/') : current.replace('/', '.');
                rt = MinecraftHelper.findMethodReturnType(alt, methodName);
                if (rt != null) {
                    return rt;
                }
                String parent = MC_SUPER_CLASS.getOrDefault(current, SENTINEL);
                if (!(parent != SENTINEL || (parent = MinecraftHelper.findSuperClassName(current)) == null || parent.equals("java.lang.Object") || parent.equals("java/lang/Object") || parent.equals("Obejct"))) {
                    parent = parent.replace('/', '.');
                }
                current = parent;
            }
            return null;
        }

        private String reflectMethodReturnType(Class<?> clazz, String methodName, int argCount) {
            for (Method m : clazz.getMethods()) {
                if (!m.getName().equals(methodName) || m.getParameterCount() != argCount || m.getGenericReturnType() instanceof TypeVariable || m.getReturnType() == Object.class && this.hasTypeParameters(m)) continue;
                return m.getReturnType().getName();
            }
            for (Method m : clazz.getMethods()) {
                if (!m.getName().equals(methodName) || m.getGenericReturnType() instanceof TypeVariable || m.getReturnType() == Object.class && this.hasTypeParameters(m)) continue;
                return m.getReturnType().getName();
            }
            return null;
        }

        private boolean hasTypeParameters(Method m) {
            return m.getGenericReturnType() instanceof TypeVariable || m.getDeclaringClass().getTypeParameters().length > 0 && m.getReturnType() == Object.class;
        }

        private String inferLambdaParamType(LambdaExpr lambda, int paramIndex) {
            Optional parentOpt = lambda.getParentNode();
            if (!parentOpt.isPresent() || !(parentOpt.get() instanceof MethodCallExpr)) {
                return null;
            }
            MethodCallExpr call = (MethodCallExpr)parentOpt.get();
            int argIdx = -1;
            for (int i = 0; i < call.getArguments().size(); ++i) {
                if (call.getArgument(i) != lambda) continue;
                argIdx = i;
                break;
            }
            if (argIdx < 0) {
                return null;
            }
            int finalArgIdx = argIdx;
            String methodName = call.getNameAsString();
            Optional scopeOpt = call.getScope();
            String scopeClass = scopeOpt.isPresent() ? this.resolveClassName((Expression)scopeOpt.get()) : this.getEnclosingClassName((Node)call);
            ArrayList<String> candidates = new ArrayList<String>();
            if (scopeClass != null) {
                candidates.add(scopeClass);
            }
            candidates.addAll(BRIGADIER_BUILDER_CLASSES);
            for (String candidate : candidates) {
                try {
                    Class<?> clazz = Class.forName(candidate);
                    for (Method method : clazz.getMethods()) {
                        Class<?>[] samParams;
                        Class<?> fi;
                        Method[] abstracts;
                        Class<?>[] paramTypes;
                        if (!method.getName().equals(methodName) || (paramTypes = method.getParameterTypes()).length <= finalArgIdx || (abstracts = (Method[])Arrays.stream((fi = paramTypes[finalArgIdx]).getMethods()).filter(m -> Modifier.isAbstract(m.getModifiers())).toArray(Method[]::new)).length != 1 || paramIndex >= (samParams = abstracts[0].getParameterTypes()).length) continue;
                        String resolved = samParams[paramIndex].getName();
                        LOGGER.debug("Lambda param inferred: {}.{}(arg[{}]) -> @{} SAM[{}] = {}", (Object)candidate, (Object)methodName, (Object)finalArgIdx, (Object)fi.getSimpleName(), (Object)paramIndex, (Object)resolved);
                        return resolved;
                    }
                }
                catch (ClassNotFoundException | SecurityException exception) {
                }
            }
            return null;
        }

        private String resolveVariableType(NameExpr nameExpr) {
            String varName = nameExpr.getNameAsString();
            Optional parent = nameExpr.getParentNode();
            while (parent.isPresent()) {
                Node node = (Node)parent.get();
                if (node instanceof LambdaExpr) {
                    LambdaExpr lambda = (LambdaExpr)node;
                    for (int i = 0; i < lambda.getParameters().size(); ++i) {
                        Parameter param = (Parameter)lambda.getParameters().get(i);
                        if (!param.getNameAsString().equals(varName)) continue;
                        String typeStr = param.getType().asString();
                        if (!typeStr.isEmpty() && !typeStr.equals("var")) {
                            return this.resolveFullClassName(typeStr, (Node)nameExpr);
                        }
                        String inferred = this.inferLambdaParamType(lambda, i);
                        if (inferred != null) {
                            LOGGER.debug("Inferred lambda param '{}' type: {}", (Object)varName, (Object)inferred);
                        }
                        return inferred;
                    }
                }
                if (node instanceof MethodDeclaration) {
                    String t;
                    MethodDeclaration method = (MethodDeclaration)node;
                    for (Parameter param : method.getParameters()) {
                        if (!param.getNameAsString().equals(varName)) continue;
                        return this.resolveFullClassName(param.getType().asString(), (Node)nameExpr);
                    }
                    if (method.getBody().isPresent() && (t = this.findVariableInStatementsRecursive((NodeList<Statement>)((BlockStmt)method.getBody().get()).getStatements(), varName, (Node)nameExpr)) != null) {
                        return t;
                    }
                }
                if (node instanceof ClassOrInterfaceDeclaration) {
                    ClassOrInterfaceDeclaration cd = (ClassOrInterfaceDeclaration)node;
                    for (FieldDeclaration fd : cd.getFields()) {
                        for (VariableDeclarator vd : fd.getVariables()) {
                            if (!vd.getNameAsString().equals(varName)) continue;
                            return this.resolveVarDeclaratorType(vd, (Node)nameExpr);
                        }
                    }
                }
                parent = node.getParentNode();
            }
            return null;
        }

        private String resolveVarDeclaratorType(VariableDeclarator vd, Node context) {
            String typeStr = vd.getType().asString();
            if (typeStr.equals("var")) {
                String inferred;
                if (vd.getInitializer().isPresent() && (inferred = this.resolveExpressionType((Expression)vd.getInitializer().get())) != null) {
                    LOGGER.debug("Inferred var '{}' type: {}", (Object)vd.getNameAsString(), (Object)inferred);
                    return inferred;
                }
                return null;
            }
            return this.resolveFullClassName(typeStr, context);
        }

        private String findVariableInStatementsRecursive(NodeList<Statement> stmts, String varName, Node ctx) {
            for (Statement stmt : stmts) {
                String r = null;
                if (stmt.isExpressionStmt()) {
                    Expression expr = stmt.asExpressionStmt().getExpression();
                    if (expr.isVariableDeclarationExpr()) {
                        for (VariableDeclarator vd : expr.asVariableDeclarationExpr().getVariables()) {
                            if (!vd.getNameAsString().equals(varName)) continue;
                            return this.resolveVarDeclaratorType((VariableDeclarator)vd, ctx);
                        }
                    }
                    if ((r = this.findVarInExpr(expr, varName, ctx)) != null) {
                        return r;
                    }
                }
                if (stmt.isForStmt()) {
                    for (Expression e : stmt.asForStmt().getInitialization()) {
                        Iterator<VariableDeclarator> vd;
                        if (!e.isVariableDeclarationExpr()) continue;
                        vd = e.asVariableDeclarationExpr().getVariables().iterator();
                        while (vd.hasNext()) {
                            VariableDeclarator vd2 = (VariableDeclarator)vd.next();
                            if (!vd2.getNameAsString().equals(varName)) continue;
                            return this.resolveVarDeclaratorType(vd2, ctx);
                        }
                    }
                    r = this.findVariableInStatementsRecursive(this.statementsOf(stmt.asForStmt().getBody()), varName, ctx);
                    if (r != null) {
                        return r;
                    }
                }
                if (stmt.isForEachStmt()) {
                    for (VariableDeclarator vd : stmt.asForEachStmt().getVariable().getVariables()) {
                        if (!vd.getNameAsString().equals(varName)) continue;
                        return this.resolveVarDeclaratorType(vd, ctx);
                    }
                    r = this.findVariableInStatementsRecursive(this.statementsOf(stmt.asForEachStmt().getBody()), varName, ctx);
                    if (r != null) {
                        return r;
                    }
                }
                if (stmt.isTryStmt()) {
                    TryStmt ts = stmt.asTryStmt();
                    for (Expression res : ts.getResources()) {
                        if (!res.isVariableDeclarationExpr()) continue;
                        for (VariableDeclarator vd : res.asVariableDeclarationExpr().getVariables()) {
                            if (!vd.getNameAsString().equals(varName)) continue;
                            return this.resolveVarDeclaratorType(vd, ctx);
                        }
                    }
                    r = this.findVariableInStatementsRecursive((NodeList<Statement>)ts.getTryBlock().getStatements(), varName, ctx);
                    if (r != null) {
                        return r;
                    }
                    for (CatchClause c : ts.getCatchClauses()) {
                        r = this.findVariableInStatementsRecursive((NodeList<Statement>)c.getBody().getStatements(), varName, ctx);
                        if (r == null) continue;
                        return r;
                    }
                    if (ts.getFinallyBlock().isPresent() && (r = this.findVariableInStatementsRecursive((NodeList<Statement>)((BlockStmt)ts.getFinallyBlock().get()).getStatements(), varName, ctx)) != null) {
                        return r;
                    }
                }
                if (stmt.isIfStmt()) {
                    r = this.findVariableInStatementsRecursive(this.statementsOf(stmt.asIfStmt().getThenStmt()), varName, ctx);
                    if (r != null) {
                        return r;
                    }
                    if (stmt.asIfStmt().getElseStmt().isPresent() && (r = this.findVariableInStatementsRecursive(this.statementsOf((Statement)stmt.asIfStmt().getElseStmt().get()), varName, ctx)) != null) {
                        return r;
                    }
                }
                if (stmt.isWhileStmt() && (r = this.findVariableInStatementsRecursive(this.statementsOf(stmt.asWhileStmt().getBody()), varName, ctx)) != null) {
                    return r;
                }
                if (stmt.isBlockStmt() && (r = this.findVariableInStatementsRecursive((NodeList<Statement>)stmt.asBlockStmt().getStatements(), varName, ctx)) != null) {
                    return r;
                }
                if (!stmt.isReturnStmt() || !stmt.asReturnStmt().getExpression().isPresent() || (r = this.findVarInExpr((Expression)stmt.asReturnStmt().getExpression().get(), varName, ctx)) == null) continue;
                return r;
            }
            return null;
        }

        private String findVarInExpr(Expression expr, String varName, Node ctx) {
            if (expr instanceof LambdaExpr) {
                LambdaExpr lambda = (LambdaExpr)expr;
                if (lambda.getBody().isBlockStmt()) {
                    return this.findVariableInStatementsRecursive((NodeList<Statement>)lambda.getBody().asBlockStmt().getStatements(), varName, ctx);
                }
                return null;
            }
            if (expr instanceof MethodCallExpr) {
                String r;
                MethodCallExpr call = (MethodCallExpr)expr;
                for (Expression a : call.getArguments()) {
                    String r2 = this.findVarInExpr(a, varName, ctx);
                    if (r2 == null) continue;
                    return r2;
                }
                if (call.getScope().isPresent() && (r = this.findVarInExpr((Expression)call.getScope().get(), varName, ctx)) != null) {
                    return r;
                }
                return null;
            }
            if (expr instanceof VariableDeclarationExpr) {
                for (VariableDeclarator vd : ((VariableDeclarationExpr)expr).getVariables()) {
                    if (!vd.getNameAsString().equals(varName)) continue;
                    return this.resolveVarDeclaratorType(vd, ctx);
                }
            }
            return null;
        }

        private NodeList<Statement> statementsOf(Statement s) {
            if (s.isBlockStmt()) {
                return s.asBlockStmt().getStatements();
            }
            NodeList l = new NodeList();
            l.add((Node)s);
            return l;
        }

        private String buildMethodDescriptor(MethodCallExpr n) {
            if (n.getArguments().isEmpty()) {
                return "()";
            }
            StringBuilder sb = new StringBuilder("(");
            for (Expression a : n.getArguments()) {
                String t = this.resolveExpressionType(a);
                if (t == null) {
                    return null;
                }
                sb.append(this.toJvmDescriptor(t));
            }
            return sb.append(")").toString();
        }

        private String toJvmDescriptor(String t) {
            switch (t) {
                case "int": {
                    return "I";
                }
                case "long": {
                    return "J";
                }
                case "float": {
                    return "F";
                }
                case "double": {
                    return "D";
                }
                case "boolean": {
                    return "Z";
                }
                case "char": {
                    return "C";
                }
                case "byte": {
                    return "B";
                }
                case "short": {
                    return "S";
                }
                case "void": {
                    return "V";
                }
            }
            if (t.endsWith("[]")) {
                return "[" + this.toJvmDescriptor(t.substring(0, t.length() - 2));
            }
            return "L" + t.replace('.', '/') + ";";
        }

        private String resolveExpressionType(Expression e) {
            if (e instanceof IntegerLiteralExpr) {
                return "int";
            }
            if (e instanceof LongLiteralExpr) {
                return "long";
            }
            if (e instanceof DoubleLiteralExpr) {
                return "double";
            }
            if (e instanceof BooleanLiteralExpr) {
                return "boolean";
            }
            if (e instanceof CharLiteralExpr) {
                return "char";
            }
            if (e instanceof StringLiteralExpr) {
                return "java.lang.String";
            }
            if (e instanceof NullLiteralExpr) {
                return "java.lang.Object";
            }
            if (e instanceof CastExpr) {
                return this.resolveFullClassName(((CastExpr)e).getType().asString(), (Node)e);
            }
            if (e instanceof ObjectCreationExpr) {
                return this.resolveFullClassName(((ObjectCreationExpr)e).getType().asString(), (Node)e);
            }
            if (e instanceof NameExpr) {
                return this.resolveVariableType((NameExpr)e);
            }
            if (e instanceof MethodCallExpr) {
                return this.resolveMethodReturnType((MethodCallExpr)e);
            }
            if (e instanceof FieldAccessExpr) {
                return this.resolveFieldType((FieldAccessExpr)e);
            }
            return null;
        }

        private String resolveFullClassName(String name, Node ctx) {
            if (name == null || name.isEmpty()) {
                return null;
            }
            if (name.contains(".")) {
                return name;
            }
            Optional parent = ctx.getParentNode();
            while (parent.isPresent()) {
                if (parent.get() instanceof CompilationUnit) {
                    CompilationUnit cu = (CompilationUnit)parent.get();
                    for (ImportDeclaration imp : cu.getImports()) {
                        String in = imp.getNameAsString();
                        if (!imp.isAsterisk()) {
                            if (in.endsWith("." + name)) {
                                return in;
                            }
                            if (!in.endsWith("$" + name)) continue;
                            return in.replace("$", ".");
                        }
                        String pkg = in.substring(0, in.lastIndexOf(46));
                        String full = pkg + "." + name;
                        if (this.shouldTransform(full)) {
                            return full;
                        }
                        try {
                            Class.forName(full);
                            return full;
                        }
                        catch (ClassNotFoundException classNotFoundException) {
                        }
                    }
                    if (!cu.getPackageDeclaration().isPresent()) break;
                    String full = ((PackageDeclaration)cu.getPackageDeclaration().get()).getNameAsString() + "." + name;
                    if (this.shouldTransform(full)) {
                        return full;
                    }
                    try {
                        Class.forName(full);
                        return full;
                    }
                    catch (ClassNotFoundException classNotFoundException) {
                        break;
                    }
                }
                parent = ((Node)parent.get()).getParentNode();
            }
            try {
                Class.forName("java.lang." + name);
                return "java.lang." + name;
            }
            catch (ClassNotFoundException classNotFoundException) {
                return name;
            }
        }

        private String getEnclosingClassName(Node node) {
            Optional p = node.getParentNode();
            while (p.isPresent()) {
                if (p.get() instanceof ClassOrInterfaceDeclaration) {
                    return this.getFullyQualifiedName((ClassOrInterfaceDeclaration)p.get());
                }
                p = ((Node)p.get()).getParentNode();
            }
            return null;
        }

        private String getFullyQualifiedName(ClassOrInterfaceDeclaration cd) {
            String pkg = "";
            Optional p = cd.getParentNode();
            while (p.isPresent()) {
                if (p.get() instanceof CompilationUnit) {
                    CompilationUnit cu = (CompilationUnit)p.get();
                    if (!cu.getPackageDeclaration().isPresent()) break;
                    pkg = ((PackageDeclaration)cu.getPackageDeclaration().get()).getNameAsString();
                    break;
                }
                p = ((Node)p.get()).getParentNode();
            }
            return pkg.isEmpty() ? cd.getNameAsString() : pkg + "." + cd.getNameAsString();
        }

        private String reconstructQualifiedName(Expression expr) {
            if (expr instanceof NameExpr) {
                return ((NameExpr)expr).getNameAsString();
            }
            if (expr instanceof FieldAccessExpr) {
                FieldAccessExpr fa = (FieldAccessExpr)expr;
                String s = this.reconstructQualifiedName(fa.getScope());
                return s == null ? null : s + "." + fa.getNameAsString();
            }
            return null;
        }

        private String resolveFieldType(FieldAccessExpr fa) {
            String base = this.resolveClassName(fa.getScope());
            if (base == null) {
                return null;
            }
            String fieldName = fa.getNameAsString();
            for (String cls : new String[]{base, base.contains(".") ? base.replace('.', '/') : base.replace('/', '.')}) {
                String ft = MinecraftHelper.findFieldType(cls, fieldName);
                if (ft == null) continue;
                return ft.replace('/', '.');
            }
            try {
                return Class.forName(base).getDeclaredField(fieldName).getType().getName();
            }
            catch (Exception exception) {
                return null;
            }
        }

        static {
            KNOWN_RETURN_TYPES.put("com.mojang.brigadier.context.CommandContext|getSource", "net.minecraft.commands.CommandSourceStack");
            MC_SUPER_CLASS = new HashMap<String, String>();
            MC_SUPER_CLASS.put("net.minecraft.server.level.ServerPlayer", "net.minecraft.world.entity.player.Player");
            MC_SUPER_CLASS.put("net.minecraft.client.player.LocalPlayer", "net.minecraft.world.entity.player.AbstractClientPlayer");
            MC_SUPER_CLASS.put("net.minecraft.client.player.AbstractClientPlayer", "net.minecraft.world.entity.player.Player");
            MC_SUPER_CLASS.put("net.minecraft.world.entity.player.Player", "net.minecraft.world.entity.LivingEntity");
            MC_SUPER_CLASS.put("net.minecraft.world.entity.LivingEntity", "net.minecraft.world.entity.Entity");
            MC_SUPER_CLASS.put("net.minecraft.world.entity.Mob", "net.minecraft.world.entity.LivingEntity");
            MC_SUPER_CLASS.put("net.minecraft.world.entity.PathfinderMob", "net.minecraft.world.entity.Mob");
            MC_SUPER_CLASS.put("net.minecraft.world.entity.monster.Monster", "net.minecraft.world.entity.PathfinderMob");
            MC_SUPER_CLASS.put("net.minecraft.world.entity.animal.Animal", "net.minecraft.world.entity.PathfinderMob");
            MC_SUPER_CLASS.put("net.minecraft.world.level.block.entity.BlockEntity", null);
            MC_SUPER_CLASS.put("net.minecraft.server.level.ServerLevel", "net.minecraft.world.level.Level");
            MC_SUPER_CLASS.put("net.minecraft.world.level.Level", null);
            MC_SUPER_CLASS.put("net.minecraft.commands.CommandSourceStack", null);
            BRIGADIER_BUILDER_CLASSES = Arrays.asList("com.mojang.brigadier.builder.ArgumentBuilder", "com.mojang.brigadier.builder.LiteralArgumentBuilder", "com.mojang.brigadier.builder.RequiredArgumentBuilder", "com.mojang.brigadier.CommandDispatcher");
            TRANSFORM_PACKAGES = new String[]{"net.minecraft.", "com.mojang.", "net/minecraft/", "com/mojang/"};
        }
    }
}

