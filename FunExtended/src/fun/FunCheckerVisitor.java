//////////////////////////////////////////////////////////////
//
// A visitor for contextual analysis of Fun.
//
// Based on a previous version developed by
// David Watt and Simon Gay (University of Glasgow).
//
// Modified for PL Coursework Assignment
// Name: Bin Zhang
// Student ID: 2941833z
// Date: 14th May 2024
//
//////////////////////////////////////////////////////////////

package fun;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.w3c.dom.ranges.Range;
import org.antlr.v4.runtime.misc.*;

import java.util.Arrays;
import java.util.List;

// EXTENSION B: Add needed library
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
// END OF EXTENSION

import ast.*;
import fun.Type.Pair;

public class FunCheckerVisitor extends AbstractParseTreeVisitor<Type> implements FunVisitor<Type> {

	// EXTENSION B: Add for analysing switch command

	// store one integer range's start and end value
	private static class Pair<T1, T2> {
        final T1 key;
        final T2 value;

        Pair(T1 key, T2 value) {
            this.key = key;
            this.value = value;
        }

        // Getter method
        public T1 getKey() { return key; }
        public T2 getValue() { return value; }
    }

	// record the expression type
	Type switchType;

	// store all cases' int, bool, range value
	private Set<Integer> intCases = new HashSet<>(); 
	private Set<Boolean> boolCases = new HashSet<>(); 
	private List<Pair<Integer, Integer>> rangeCases = new ArrayList<>(); 

	// END OF EXTENSION


	// Contextual errors

	private int errorCount = 0;

	private CommonTokenStream tokens;

	// Constructor

	public FunCheckerVisitor(CommonTokenStream toks) {
	    tokens = toks;
	}

	private void reportError (String message,
	                          ParserRuleContext ctx) {
	// Print an error message relating to the given 
	// part of the AST.
	    Interval interval = ctx.getSourceInterval();
	    Token start = tokens.get(interval.a);
	    Token finish = tokens.get(interval.b);
	    int startLine = start.getLine();
	    int startCol = start.getCharPositionInLine();
	    int finishLine = finish.getLine();
	    int finishCol = finish.getCharPositionInLine();
	    System.err.println(startLine + ":" + startCol + "-" +
                               finishLine + ":" + finishCol
		   + " " + message);
		errorCount++;
	}

	public int getNumberOfContextualErrors () {
	// Return the total number of errors so far detected.
		return errorCount;
	}


	// Scope checking

	private SymbolTable<Type> typeTable =
	   new SymbolTable<Type>();

	private void predefine () {
	// Add predefined procedures to the type table.
		// ## Warm-up
		// Change read's parameter type from Type.VOID to Type.EMPTY
		typeTable.put("read",
		   new Type.Mapping(Type.EMPTY, Type.INT));

		// ## Warm-up
		// Change write's parameter type to be a Type.Sequence containing just Type.INT
		ArrayList<Type> ints = new ArrayList<>();
		ints.add(Type.INT);
		Type.Sequence writeParamType = new Type.Sequence(ints);
		typeTable.put("write",
		   new Type.Mapping(writeParamType, Type.VOID));
	}

	private void define (String id, Type type,
	                     ParserRuleContext decl) {
	// Add id with its type to the type table, checking 
	// that id is not already declared in the same scope.
		boolean ok = typeTable.put(id, type);
		if (!ok)
			reportError(id + " is redeclared", decl);
	}

	private Type retrieve (String id, ParserRuleContext occ) {
	// Retrieve id's type from the type table.
		Type type = typeTable.get(id);
		if (type == null) {
			reportError(id + " is undeclared", occ);
			return Type.ERROR;
		} else
			return type;
	}

	// Type checking

	private static final Type.Mapping
	   NOTTYPE = new Type.Mapping(Type.BOOL, Type.BOOL),
	   COMPTYPE = new Type.Mapping(
	      new Type.Pair(Type.INT, Type.INT), Type.BOOL),
	   ARITHTYPE = new Type.Mapping(
	      new Type.Pair(Type.INT, Type.INT), Type.INT),
	   // ## Warm-up
	   // Change read's parameter type from Type.VOID to Type.EMPTY
	   MAINTYPE = new Type.Mapping(Type.EMPTY, Type.VOID);

	private void checkType (Type typeExpected,
	                        Type typeActual,
	                        ParserRuleContext construct) {
	// Check that a construct's actual type matches 
	// the expected type.
		if (! typeActual.equiv(typeExpected))
			reportError("type is " + typeActual
			   + ", should be " + typeExpected,
			   construct);
	}

	private Type checkCall (String id, Type typeArg,
	                        ParserRuleContext call) {
	// Check that a procedure call identifies a procedure 
	// and that its argument type matches the proecure's 
	// type. Return the type of the procedure call.
		Type typeProc = retrieve(id, call);
		if (! (typeProc instanceof Type.Mapping)) {
			reportError(id + " is not a procedure", call);
			return Type.ERROR;
		} else {
			Type.Mapping mapping = (Type.Mapping)typeProc;
			checkType(mapping.domain, typeArg, call);
			return mapping.range;
		}
	}

	private Type checkUnary (Type.Mapping typeOp,
	                         Type typeArg,
	                         ParserRuleContext op) {
	// Check that a unary operator's operand type matches 
	// the operator's type. Return the type of the operator 
	// application.
		if (! (typeOp.domain instanceof Type.Primitive))
			reportError(
			   "unary operator should have 1 operand",
			   op);
		else
			checkType(typeOp.domain, typeArg, op);
		return typeOp.range;
	}

	private Type checkBinary (Type.Mapping typeOp,
	                          Type typeArg1, Type typeArg2,
	                          ParserRuleContext op) {
	// Check that a binary operator's operand types match 
	// the operator's type. Return the type of the operator 
	// application.
		if (! (typeOp.domain instanceof Type.Pair))
			reportError(
			   "binary operator should have 2 operands",
			   op);
		else {
			Type.Pair pair =
			   (Type.Pair)typeOp.domain;
			checkType(pair.first, typeArg1, op);
			checkType(pair.second, typeArg2, op);
		}
		return typeOp.range;
	}

	/**
	 * Visit a parse tree produced by the {@code prog}
	 * labeled alternative in {@link FunParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitProg(FunParser.ProgContext ctx) {
	    predefine();
	    visitChildren(ctx);
	    Type tmain = retrieve("main", ctx);
	    checkType(MAINTYPE, tmain, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code proc}
	 * labeled alternative in {@link FunParser#proc_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitProc(FunParser.ProcContext ctx) {
	    typeTable.enterLocalScope();
	    Type t;
		// ## Warm-up
		// Change ctx.formal_decl() to ctx.formal_decl_seq()
	    FunParser.Formal_decl_seqContext fd = ctx.formal_decl_seq();
	    if (fd != null)
		t = visit(fd);
	    else
		// ## Warm-up
		// Change Type.VOID to Type.EMPTY
		t = Type.EMPTY;
	    Type proctype = new Type.Mapping(t, Type.VOID);
	    define(ctx.ID().getText(), proctype, ctx);
	    List<FunParser.Var_declContext> var_decl = ctx.var_decl();
	    for (FunParser.Var_declContext vd : var_decl)
		visit(vd);
	    visit(ctx.seq_com());
	    typeTable.exitLocalScope();
	    define(ctx.ID().getText(), proctype, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code func}
	 * labeled alternative in {@link FunParser#proc_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitFunc(FunParser.FuncContext ctx) {
	    typeTable.enterLocalScope();
	    Type t1 = visit(ctx.type());
	    Type t2;
		// ## Warm-up
		// Change ctx.formal_decl() to ctx.formal_decl_seq()
	    FunParser.Formal_decl_seqContext fd = ctx.formal_decl_seq();
	    if (fd != null)
		t2 = visit(fd);
	    else
		// ## Warm-up
		// Change Type.VOID to Type.EMPTY
		t2 = Type.EMPTY;
	    Type functype = new Type.Mapping(t2, t1);
	    define(ctx.ID().getText(), functype, ctx);
	    List<FunParser.Var_declContext> var_decl = ctx.var_decl();
	    for (FunParser.Var_declContext vd : var_decl)
		visit(vd);
	    visit(ctx.seq_com());
	    Type returntype = visit(ctx.expr());
	    checkType(t1, returntype, ctx);
	    typeTable.exitLocalScope();
	    define(ctx.ID().getText(), functype, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code formal}
	 * labeled alternative in {@link FunParser#formal_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitFormal(FunParser.FormalContext ctx) {
		// ## Warm-up
		// Simplify the method visitFormal()
	    Type t = visit(ctx.type());
    	define(ctx.ID().getText(), t, ctx);
    	return t;
	}

	/**
	 * Visit a parse tree produced by the {@code var}
	 * labeled alternative in {@link FunParser#var_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitVar(FunParser.VarContext ctx) {
	    Type t1 = visit(ctx.type());
	    Type t2 = visit(ctx.expr());
	    define(ctx.ID().getText(), t1, ctx);
	    checkType(t1, t2, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code bool}
	 * labeled alternative in {@link FunParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitBool(FunParser.BoolContext ctx) {
	    return Type.BOOL;
	}

	/**
	 * Visit a parse tree produced by the {@code int}
	 * labeled alternative in {@link FunParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitInt(FunParser.IntContext ctx) {
	    return Type.INT;
	}

	/**
	 * Visit a parse tree produced by the {@code assn}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitAssn(FunParser.AssnContext ctx) {
	    Type tvar = retrieve(ctx.ID().getText(), ctx);
	    Type t = visit(ctx.expr());
	    checkType(tvar, t, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code proccall}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitProccall(FunParser.ProccallContext ctx) {
		Type t;

		// ## Warm-up
		// Check if ctx.actual_seq() return null
	    if (ctx.actual_seq() != null) {
			t = visit(ctx.actual_seq());
		} else {
			t = new Type.Sequence(new ArrayList<>()); 
		}

	    Type tres = checkCall(ctx.ID().getText(), t, ctx);
	    if (! tres.equiv(Type.VOID))
		reportError("procedure should be void", ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code if}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitIf(FunParser.IfContext ctx) {
	    Type t = visit(ctx.expr());
	    visit(ctx.c1);
	    if (ctx.c2 != null)
		visit(ctx.c2);
	    checkType(Type.BOOL, t, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code while}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitWhile(FunParser.WhileContext ctx) {
	    Type t = visit(ctx.expr());
	    visit(ctx.seq_com());
	    checkType(Type.BOOL, t, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code seq}
	 * labeled alternative in {@link FunParser#seq_com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitSeq(FunParser.SeqContext ctx) {
	    visitChildren(ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by {@link FunParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitExpr(FunParser.ExprContext ctx) {
	    Type t1 = visit(ctx.e1);
	    if (ctx.e2 != null) {
		Type t2 = visit(ctx.e2);
		return checkBinary(COMPTYPE, t1, t2, ctx);
		// COMPTYPE is INT x INT -> BOOL
		// checkBinary checks that t1 and t2 are INT and returns BOOL
		// If necessary it produces an error message.
	    }
	    else {
		return t1;
	    }
	}

	/**
	 * Visit a parse tree produced by {@link FunParser#sec_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitSec_expr(FunParser.Sec_exprContext ctx) {
	    Type t1 = visit(ctx.e1);
	    if (ctx.e2 != null) {
		Type t2 = visit(ctx.e2);
		return checkBinary(ARITHTYPE, t1, t2, ctx);
	    }
	    else {
		return t1;
	    }
	}

	/**
	 * Visit a parse tree produced by the {@code false}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitFalse(FunParser.FalseContext ctx) {
	    return Type.BOOL;
	}

	/**
	 * Visit a parse tree produced by the {@code true}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitTrue(FunParser.TrueContext ctx) {
	    return Type.BOOL;
	}

	/**
	 * Visit a parse tree produced by the {@code num}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitNum(FunParser.NumContext ctx) {
	    return Type.INT;
	}

	/**
	 * Visit a parse tree produced by the {@code id}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitId(FunParser.IdContext ctx) {
	    return retrieve(ctx.ID().getText(), ctx);
	}

	/**
	 * Visit a parse tree produced by the {@code funccall}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitFunccall(FunParser.FunccallContext ctx) {
	    Type t;

		// ## Warm-up
		// Check if ctx.actual_seq() return null
		if (ctx.actual_seq() != null) {
			t = visit(ctx.actual_seq());
		} else {
			t = new Type.Sequence(new ArrayList<>()); 
		}

	    Type tres = checkCall(ctx.ID().getText(), t, ctx);
	    if (tres.equiv(Type.VOID))
		reportError("procedure should be non-void", ctx);
	    return tres;
	}

	/**
	 * Visit a parse tree produced by the {@code not}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitNot(FunParser.NotContext ctx) {
	    Type t = visit(ctx.prim_expr());
	    return checkUnary(NOTTYPE, t, ctx);
	}

	/**
	 * Visit a parse tree produced by the {@code parens}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitParens(FunParser.ParensContext ctx) {
	    return visit(ctx.expr());
	}

	/**
	 * Visit a parse tree produced by {@link FunParser#actual}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */

	// ## Warm-up
	// Replace visitActual() by visitActualseq()
	public Type visitActualseq(FunParser.ActualseqContext ctx) {
	    ArrayList<Type> types = new ArrayList<>();
    	for (FunParser.ExprContext ec : ctx.expr()) {
        	types.add(visit(ec));
    	}
    	return new Type.Sequence(types);
	}

	// ## Warm-up
	// Add a method visitFormalseq()
	public Type visitFormalseq(FunParser.FormalseqContext ctx) {
		ArrayList<Type> types = new ArrayList<>();
		for (FunParser.Formal_declContext fdc : ctx.formal_decl()) {
			types.add(visit(fdc));
		}
		return new Type.Sequence(types);
	}
	
	// EXTENSION: Extension A - Checker of repeat-until command

	public Type visitRepeat(FunParser.RepeatContext ctx) {
		// Visit and internal commands within the repeat loop
		visit(ctx.seq_com());
		
		// Visit the condition expression evaluating
		Type exprType = visit(ctx.expr());

		// Check if the condition expression is of boolean type. If not, report an error.
		checkType(Type.BOOL, exprType, ctx);

		return null; 
	}

	// END OF EXTENSION


	// EXTENSION: Extension B - Checker of switch command

	public Type visitSwitch(FunParser.SwitchContext ctx) {
		// Ensure the switch expression is of a compatible type (INT or BOOL)
		switchType = visit(ctx.expr()); 

		intCases.clear();
    	boolCases.clear();
    	rangeCases.clear();

		if (!(switchType.equiv(Type.INT) || switchType.equiv(Type.BOOL))) {
            reportError("Switch expression must be either INT or BOOL.", ctx.expr());
        }
		
		// Visit and analyze each case within the switch.
		visitCaseseq((FunParser.CaseseqContext)ctx.case_seq());
        
        // Ensure there's a default case.
        visitDefaultcase((FunParser.DefaultcaseContext)ctx.default_case());
        
        return null;
	}

	// Visit all cases -- firstly check type and value, then visit internal commands
	public Type visitCaseseq(FunParser.CaseseqContext ctx) {
        for (FunParser.Case_comContext caseCtx : ctx.case_com()) {
            visitCase((FunParser.CaseContext)caseCtx);
        }

		for (FunParser.Case_comContext caseCtx : ctx.case_com()) {
            visitCasecom((FunParser.CaseContext)caseCtx);
        }

        return null;
    }

	// Check for type conflicts and duplicate values by visiting one case
	public Type visitCase(FunParser.CaseContext ctx) {
		// Record the case value's type
		Type caseType = null;

		// Check case for int, bool, range type
		if (ctx.NUM() != null) {
			// For int case
			caseType = Type.INT;
			int caseValue = Integer.parseInt(ctx.NUM().getText());
			if (!intCases.add(caseValue)) {
				// Duplicate occur
				reportError("Duplicate case value: " + caseValue, ctx);
			}
		} else if (ctx.TRUE() != null) {
			// For bool case -- true
			caseType = Type.BOOL;
			boolean caseValue = Boolean.parseBoolean(ctx.TRUE().getText());
			if (!boolCases.add(caseValue)) {
				// Duplicate occur
				reportError("Duplicate case value: " + caseValue, ctx);
			}
		}else if (ctx.FALSE() != null) {
			// For bool case -- false
			caseType = Type.BOOL;
			boolean caseValue = Boolean.parseBoolean(ctx.FALSE().getText());
			if (!boolCases.add(caseValue)) {
				// Duplicate occur
				reportError("Duplicate case value: " + caseValue, ctx);
			}
		}else if (ctx.range() != null) {
			caseType = Type.INT;
			visitRangeofint((FunParser.RangeofintContext)ctx.range());
		}

		// Check if case type matches switch expression type
		if (switchType != null && !switchType.equiv(caseType)) {
			reportError("Case type " + caseType + " does not match switch expression type " + switchType, ctx);
		}
		
		return null; 
	}

	// Check the internal commands after each case
	public Type visitCasecom(FunParser.CaseContext ctx) {
		visit(ctx.seq_com());
		return null; 
	}

	// Visit each range value and store it to the ArrayList created above
	public Type visitRangeofint(FunParser.RangeofintContext ctx) {
		int start = Integer.parseInt(ctx.NUM(0).getText());
		int end = Integer.parseInt(ctx.NUM(1).getText());
	
		// Ensure that the start of the range <= end
		if (start > end) {
			reportError("Range start is greater than end in case guard", ctx);
			return null;
		}
	
		// Check if the new range overlaps with the existing range
		for (Pair<Integer, Integer> existingRange : rangeCases) {
			if (start <= existingRange.getValue() && end >= existingRange.getKey()) {
				reportError("Case range overlaps with range in case guard", ctx);
				return null;
			}
		}
	
		// Check if the new range overlaps with the existing value
		for (Integer val : intCases) {
			if (val >= start && val <= end) {
				reportError("Case value overlaps with range in case guard", ctx);
				return null;
			}
		}
	
		// Creat a new Pair and add the new range to ArrayList sturcture
		rangeCases.add(new Pair<>(start, end));
	
		return null;
	}

	// Check the internal commands for default case
	public Type visitDefaultcase(FunParser.DefaultcaseContext ctx) {
        // Process the sequence of commands within the default case.
        visit(ctx.seq_com());
        return null;
    }

	// END OF EXTENSION

}
