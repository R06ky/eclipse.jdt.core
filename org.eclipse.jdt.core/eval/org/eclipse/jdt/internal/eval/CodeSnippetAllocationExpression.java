/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.eval;

import org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import org.eclipse.jdt.internal.compiler.codegen.CodeStream;
import org.eclipse.jdt.internal.compiler.flow.FlowInfo;
import org.eclipse.jdt.internal.compiler.lookup.ArrayBinding;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemMethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemReasons;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;

public class CodeSnippetAllocationExpression extends AllocationExpression implements ProblemReasons, EvaluationConstants {
	EvaluationContext evaluationContext;
	FieldBinding delegateThis;
/**
 * CodeSnippetAllocationExpression constructor comment.
 */
public CodeSnippetAllocationExpression(EvaluationContext evaluationContext) {
	this.evaluationContext = evaluationContext;
}
public void generateCode(
	BlockScope currentScope, 
	CodeStream codeStream, 
	boolean valueRequired) {

	int pc = codeStream.position;
	ReferenceBinding allocatedType = this.binding.declaringClass;

	if (this.binding.canBeSeenBy(allocatedType, this, currentScope)) {
		codeStream.new_(allocatedType);
		if (valueRequired) {
			codeStream.dup();
		}
		// better highlight for allocation: display the type individually
		codeStream.recordPositionsFrom(pc, this.type.sourceStart);

		// handling innerclass instance allocation - enclosing instance arguments
		if (allocatedType.isNestedType()) {
			codeStream.generateSyntheticEnclosingInstanceValues(
				currentScope,
				allocatedType,
				enclosingInstance(),
				this);
		}
		// generate the arguments for constructor
		if (this.arguments != null) {
			for (int i = 0, count = this.arguments.length; i < count; i++) {
				this.arguments[i].generateCode(currentScope, codeStream, true);
			}
		}
		// handling innerclass instance allocation - outer local arguments
		if (allocatedType.isNestedType()) {
			codeStream.generateSyntheticOuterArgumentValues(
				currentScope,
				allocatedType,
				this);
		}
		// invoke constructor
		codeStream.invokespecial(this.binding);
	} else {
		// private emulation using reflect
		((CodeSnippetCodeStream) codeStream).generateEmulationForConstructor(currentScope, this.binding);
		// generate arguments
		if (this.arguments != null) {
			int argsLength = this.arguments.length;
			codeStream.generateInlinedValue(argsLength);
			codeStream.newArray(currentScope, new ArrayBinding(currentScope.getType(TypeConstants.JAVA_LANG_OBJECT), 1));
			codeStream.dup();
			for (int i = 0; i < argsLength; i++) {
				codeStream.generateInlinedValue(i);
				this.arguments[i].generateCode(currentScope, codeStream, true);
				TypeBinding parameterBinding = this.binding.parameters[i];
				if (parameterBinding.isBaseType() && parameterBinding != NullBinding) {
					((CodeSnippetCodeStream)codeStream).generateObjectWrapperForType(this.binding.parameters[i]);
				}
				codeStream.aastore();
				if (i < argsLength - 1) {
					codeStream.dup();
				}	
			}
		} else {
			codeStream.generateInlinedValue(0);
			codeStream.newArray(currentScope, new ArrayBinding(currentScope.getType(TypeConstants.JAVA_LANG_OBJECT), 1));			
		}
		((CodeSnippetCodeStream) codeStream).invokeJavaLangReflectConstructorNewInstance();
		codeStream.checkcast(allocatedType);
	}
	codeStream.recordPositionsFrom(pc, this.sourceStart);
}
/* Inner emulation consists in either recording a dependency 
 * link only, or performing one level of propagation.
 *
 * Dependency mechanism is used whenever dealing with source target
 * types, since by the time we reach them, we might not yet know their
 * exact need.
 */
public void manageEnclosingInstanceAccessIfNecessary(BlockScope currentScope, FlowInfo flowInfo) {
	// not supported yet
}
public void manageSyntheticAccessIfNecessary(BlockScope currentScope, FlowInfo flowInfo) {
	// do nothing
}
public TypeBinding resolveType(BlockScope scope) {
	// Propagate the type checking to the arguments, and check if the constructor is defined.
	this.constant = NotAConstant;
	this.resolvedType = this.type.resolveType(scope); // will check for null after args are resolved

	// buffering the arguments' types
	TypeBinding[] argumentTypes = NoParameters;
	if (this.arguments != null) {
		boolean argHasError = false;
		int length = this.arguments.length;
		argumentTypes = new TypeBinding[length];
		for (int i = 0; i < length; i++) {
			if ((argumentTypes[i] = this.arguments[i].resolveType(scope)) == null) {
				argHasError = true;
			}
		}
		if (argHasError) {
			return this.resolvedType;
		}
	}
	if (this.resolvedType == null) {
		return null;
	}
	if (!this.resolvedType.canBeInstantiated()) {
		scope.problemReporter().cannotInstantiate(this.type, this.resolvedType);
		return this.resolvedType;
	}
	ReferenceBinding allocatedType = (ReferenceBinding) this.resolvedType;
	if (!(this.binding = scope.getConstructor(allocatedType, argumentTypes, this)).isValidBinding()) {
		if (this.binding instanceof ProblemMethodBinding
			&& ((ProblemMethodBinding) this.binding).problemId() == NotVisible) {
			if (this.evaluationContext.declaringTypeName != null) {
				this.delegateThis = scope.getField(scope.enclosingSourceType(), DELEGATE_THIS, this);
				if (this.delegateThis == null) {
					if (this.binding.declaringClass == null) {
						this.binding.declaringClass = allocatedType;
					}
					scope.problemReporter().invalidConstructor(this, this.binding);
					return this.resolvedType;
				}
			} else {
				if (this.binding.declaringClass == null) {
					this.binding.declaringClass = allocatedType;
				}
				scope.problemReporter().invalidConstructor(this, this.binding);
				return this.resolvedType;
			}
			CodeSnippetScope localScope = new CodeSnippetScope(scope);			
			MethodBinding privateBinding = localScope.getConstructor((ReferenceBinding)this.delegateThis.type, argumentTypes, this);
			if (!privateBinding.isValidBinding()) {
				if (this.binding.declaringClass == null) {
					this.binding.declaringClass = allocatedType;
				}
				scope.problemReporter().invalidConstructor(this, this.binding);
				return this.resolvedType;
			} else {
				this.binding = privateBinding;
			}				
		} else {
			if (this.binding.declaringClass == null) {
				this.binding.declaringClass = allocatedType;
			}
			scope.problemReporter().invalidConstructor(this, this.binding);
			return this.resolvedType;
		}
	}
	if (isMethodUseDeprecated(this.binding, scope)) {
		scope.problemReporter().deprecatedMethod(this.binding, this);
	}
	if (this.arguments != null) {
		for (int i = 0; i < this.arguments.length; i++) {
			this.arguments[i].implicitWidening(this.binding.parameters[i], argumentTypes[i]);
		}
	}
	return allocatedType;
}
}
