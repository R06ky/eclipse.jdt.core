/*******************************************************************************
 * Copyright (c) 2016, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * This is an implementation of an early-draft specification developed under the Java
 * Community Process (JCP) and is made available for testing and evaluation purposes
 * only. The code is not compatible with any specification of the JCP.
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.core.tests.model;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.ReferenceMatch;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeReferenceMatch;
import junit.framework.Test;

/**
 * Non-regression tests for bugs fixed in Java Search engine.
 */
public class JavaSearchBugs9Tests extends AbstractJavaSearchTests {

	static {
//	 org.eclipse.jdt.internal.core.search.BasicSearchEngine.VERBOSE = true;
//	TESTS_NAMES = new String[] {"testBug501162_001"};
}

public JavaSearchBugs9Tests(String name) {
	super(name);
	this.endChar = "";
}
public static Test suite() {
	return buildModelTestSuite(JavaSearchBugs9Tests.class, BYTECODE_DECLARATION_ORDER);
}
class TestCollector extends JavaSearchResultCollector {
	public void acceptSearchMatch(SearchMatch searchMatch) throws CoreException {
		super.acceptSearchMatch(searchMatch);
	}
}
class ReferenceCollector extends JavaSearchResultCollector {
	protected void writeLine() throws CoreException {
		super.writeLine();
		ReferenceMatch refMatch = (ReferenceMatch) this.match;
		IJavaElement localElement = refMatch.getLocalElement();
		if (localElement != null) {
			this.line.append("+[");
			if (localElement.getElementType() == IJavaElement.ANNOTATION) {
				this.line.append('@');
				this.line.append(localElement.getElementName());
				this.line.append(" on ");
				this.line.append(localElement.getParent().getElementName());
			} else {
				this.line.append(localElement.getElementName());
			}
			this.line.append(']');
		}
	}

}
class TypeReferenceCollector extends ReferenceCollector {
	protected void writeLine() throws CoreException {
		super.writeLine();
		TypeReferenceMatch typeRefMatch = (TypeReferenceMatch) this.match;
		IJavaElement[] others = typeRefMatch.getOtherElements();
		int length = others==null ? 0 : others.length;
		if (length > 0) {
			this.line.append("+[");
			for (int i=0; i<length; i++) {
				IJavaElement other = others[i];
				if (i>0) this.line.append(',');
				if (other.getElementType() == IJavaElement.ANNOTATION) {
					this.line.append('@');
					this.line.append(other.getElementName());
					this.line.append(" on ");
					this.line.append(other.getParent().getElementName());
				} else {
					this.line.append(other.getElementName());
				}
			}
			this.line.append(']');
		}
	}
}

IJavaSearchScope getJavaSearchScope() {
	return SearchEngine.createJavaSearchScope(new IJavaProject[] {getJavaProject("JavaSearchBugs")});
}
IJavaSearchScope getJavaSearchScopeBugs(String packageName, boolean addSubpackages) throws JavaModelException {
	if (packageName == null) return getJavaSearchScope();
	return getJavaSearchPackageScope("JavaSearchBugs", packageName, addSubpackages);
}
public ICompilationUnit getWorkingCopy(String path, String source) throws JavaModelException {
	if (this.wcOwner == null) {
		this.wcOwner = new WorkingCopyOwner() {};
	}
	return getWorkingCopy(path, source, this.wcOwner);
}
/* (non-Javadoc)
 * @see org.eclipse.jdt.core.tests.model.SuiteOfTestCases#setUpSuite()
 */
public void setUpSuite() throws Exception {
	super.setUpSuite();
	JAVA_PROJECT = setUpJavaProject("JavaSearchBugs", "9");
}
public void tearDownSuite() throws Exception {
	deleteProject("JavaSearchBugs");
	super.tearDownSuite();
}
protected void setUp () throws Exception {
	super.setUp();
	this.resultCollector = new TestCollector();
	this.resultCollector.showAccuracy(true);
}

public void _testBug499338_001() throws CoreException {
	this.workingCopies = new ICompilationUnit[1];
	this.workingCopies[0] = getWorkingCopy("/JavaSearchBugs/src/X.java",
			"public class X {\n" +
			"    public static void main(String [] args) throws Exception {\n" +
			"    	Z z1 = new Z();\n" +
			"        try (z1;  z1) {\n" +
			"        }  \n" +
			"    }  \n" +
			"}\n" +
			"class Y implements AutoCloseable {\n" +
			"	public void close() throws Exception {\n" +
			"		System.out.println(\"Y CLOSE\");\n" +
			"	}\n" +
			"}\n" +
			"\n" +
			"class Z implements AutoCloseable {\n" +
			"	public void close() throws Exception {\n" +
			"		System.out.println(\"Z CLOSE\");\n" +
			"	}\n" +
			"}\n"
			);
	String str = this.workingCopies[0].getSource();
	String selection = "z1";
	int start = str.indexOf(selection);
	int length = selection.length();
	
	IJavaElement[] elements = this.workingCopies[0].codeSelect(start, length);
	ILocalVariable local = (ILocalVariable) elements[0];
	search(local, REFERENCES, EXACT_RULE);
	assertSearchResults(	
			"src/X.java void X.main(String[]) [z1] EXACT_MATCH\n" + 
			"src/X.java void X.main(String[]) [z1] EXACT_MATCH");	
}

public void testBug501162_001() throws Exception {
	try {

		IJavaProject project1 = createJavaProject("JavaSearchBugs9", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project1.open(null);
		addClasspathEntry(project1, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String fileContent = 
			"module first {\n" +
			"    exports pack1 to second;\n" +
			"}\n";
		createFile("/JavaSearchBugs9/src/module-info.java",	fileContent);
		createFolder("/JavaSearchBugs9/src/pack1");
		createFile("/JavaSearchBugs9/src/pack1/X11.java",
				"package pack1;\n" +
				"public class X11 {}\n");

		IJavaProject project2 = createJavaProject("second", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project2.open(null);
		addClasspathEntry(project2, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String secondFile = 
				"module second {\n" +
				"    requires first;\n" +
				"}\n";
		createFile("/second/src/module-info.java",	secondFile);

		addClasspathEntry(project1, JavaCore.newProjectEntry(project2.getPath()));
		project1.close(); // sync
		project2.close();
		project2.open(null);
		project1.open(null);
		
		IPackageFragment pkg = getPackageFragment("JavaSearchBugs9", "src", "pack1");
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaProject[] 
				{getJavaProject("JavaSearchBugs9")});

		search(
			pkg,
			ALL_OCCURRENCES,
			scope,
			this.resultCollector);
		assertSearchResults(
			"src/module-info.java first [pack1] EXACT_MATCH\n" + 
			"src/pack1 pack1 EXACT_MATCH",
			this.resultCollector);

	}
	finally {
		deleteProject("JavaSearchBugs9");
		deleteProject("second");
	}
}
public void testBug501162_002() throws Exception {
	try {

		IJavaProject project1 = createJavaProject("JavaSearchBugs9", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project1.open(null);
		addClasspathEntry(project1, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String fileContent = 
			"module first {\n" +
			"    exports pack1 to second;\n" +
			"    exports pack1 to third;\n" +
			"    opens pack1 to fourth;\n" +
			"}\n";
		createFile("/JavaSearchBugs9/src/module-info.java",	fileContent);
		createFolder("/JavaSearchBugs9/src/pack1");
		createFile("/JavaSearchBugs9/src/pack1/X11.java",
				"package pack1;\n" +
				"public class X11 {}\n");

		IJavaProject project2 = createJavaProject("second", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project2.open(null);
		addClasspathEntry(project2, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String secondFile = 
				"module second {\n" +
				"    requires first;\n" +
				"}\n";
		createFile("/second/src/module-info.java",	secondFile);

		addClasspathEntry(project1, JavaCore.newProjectEntry(project2.getPath()));
		project1.close(); // sync
		project2.close();
		project2.open(null);
		project1.open(null);
		
		IPackageFragment pkg = getPackageFragment("JavaSearchBugs9", "src", "pack1");
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaProject[] 
				{getJavaProject("JavaSearchBugs9")});

		search(
			pkg,
			ALL_OCCURRENCES,
			scope,
			this.resultCollector);
		assertSearchResults(
			"src/module-info.java first [pack1] EXACT_MATCH\n" + 
			"src/module-info.java first [pack1] EXACT_MATCH\n" + 
			"src/module-info.java first [pack1] EXACT_MATCH\n" + 
			"src/pack1 pack1 EXACT_MATCH",
			this.resultCollector);

	}
	finally {
		deleteProject("JavaSearchBugs9");
		deleteProject("second");
	}
}
public void testBug501162_003() throws Exception {
	try {

		IJavaProject project1 = createJavaProject("JavaSearchBugs9", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project1.open(null);
		addClasspathEntry(project1, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String fileContent = 
			"module first {\n" +
			"    requires second;" +
			"    provides pack22.I22 with pack1.X11;" +
			"}\n";
		createFile("/JavaSearchBugs9/src/module-info.java",	fileContent);
		createFolder("/JavaSearchBugs9/src/pack1");
		createFile("/JavaSearchBugs9/src/pack1/X11.java",
				"package pack1;\n" +
				"public class X11 implements pack22.I22{}\n");

		IJavaProject project2 = createJavaProject("second", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project2.open(null);
		addClasspathEntry(project2, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String secondFile = 
				"module second {\n" +
				"    exports pack22 to first;\n" +
				"}\n";
		createFile("/second/src/module-info.java",	secondFile);
		createFolder("/second/src/pack22");
		createFile("/second/src/pack22/I22.java",
				"package pack22;\n" +
				"public interface I22 {}\n");

		addClasspathEntry(project1, JavaCore.newProjectEntry(project2.getPath()));
		project1.close(); // sync
		project2.close();
		project2.open(null);
		project1.open(null);

		IPackageFragment pkg = getPackageFragment("second", "src", "pack22");
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaProject[] 
				{getJavaProject("JavaSearchBugs9")});

		search(
			pkg,
			REFERENCES,
			scope,
			this.resultCollector);
		assertSearchResults(
			"src/module-info.java first [pack22] EXACT_MATCH\n" + 
			"src/pack1/X11.java pack1.X11 [pack22] EXACT_MATCH\n" + 
			"src/module-info.java second [pack22] EXACT_MATCH",
			this.resultCollector);
	}
	finally {
		deleteProject("JavaSearchBugs9");
		deleteProject("second");
	}
}
public void testBug501162_005() throws CoreException {
	this.workingCopies = new ICompilationUnit[1];
	this.workingCopies[0] = getWorkingCopy("/JavaSearchBugs/src/module-info.java",
			"module first {\n" +
			"    requires second;" +
			"    provides pack22.I22 with pack1.X11;" +
			"}\n"
			);
	String str = this.workingCopies[0].getSource();
	String selection = "first";
	int start = str.indexOf(selection);
	int length = selection.length();
	
	IJavaElement[] elements = this.workingCopies[0].codeSelect(start, length);
	IModuleDescription module = (IModuleDescription) elements[0];
	search(module, ALL_OCCURRENCES, EXACT_RULE);
	assertSearchResults(	
			"src/module-info.java first [first] EXACT_MATCH");
}
public void testBug501162_006() throws Exception {
	try {

		IJavaProject project1 = createJavaProject("JavaSearchBugs9", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project1.open(null);
		addClasspathEntry(project1, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String fileContent = 
			"module first {\n" +
			"    requires second;" +
			"    provides pack22.I22 with pack1.X11;" +
			"}\n";
		createFile("/JavaSearchBugs9/src/module-info.java",	fileContent);
		createFolder("/JavaSearchBugs9/src/pack1");
		createFile("/JavaSearchBugs9/src/pack1/X11.java",
				"package pack1;\n" +
				"public class X11 implements pack22.I22{}\n");

		IJavaProject project2 = createJavaProject("second", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project2.open(null);
		addClasspathEntry(project2, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String secondFile = 
				"module second {\n" +
				"    exports pack22 to first;\n" +
				"}\n";
		createFile("/second/src/module-info.java",	secondFile);
		createFolder("/second/src/pack1");
		createFile("/second/src/pack1/I22.java",
				"package pack22;\n" +
				"public interface I22 {}\n");

		addClasspathEntry(project1, JavaCore.newProjectEntry(project2.getPath()));
		project1.close(); // sync
		project2.close();
		project2.open(null);
		project1.open(null);

		SearchPattern pattern = SearchPattern.createPattern("first", IJavaSearchConstants.MODULE, REFERENCES, ERASURE_RULE);
		IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
		search(pattern, scope, this.resultCollector);

		assertSearchResults(
				"src/module-info.java second [first] EXACT_MATCH",
			this.resultCollector);
	}
	finally {
		deleteProject("JavaSearchBugs9");
		deleteProject("second");
	}
}
public void testBug501162_007() throws Exception {
	try {

		IJavaProject project1 = createJavaProject("JavaSearchBugs9", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project1.open(null);
		addClasspathEntry(project1, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String fileContent = 
			"module first.test.org {\n" +
			"    requires second;" +
			"    provides pack22.I22 with pack1.X11;" +
			"}\n";
		createFile("/JavaSearchBugs9/src/module-info.java",	fileContent);
		createFolder("/JavaSearchBugs9/src/pack1");
		createFile("/JavaSearchBugs9/src/pack1/X11.java",
				"package pack1;\n" +
				"public class X11 implements pack22.I22{}\n");

		IJavaProject project2 = createJavaProject("second", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project2.open(null);
		addClasspathEntry(project2, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String secondFile = 
				"module second {\n" +
				"    exports pack22 to first.test.org;\n" +
				"}\n";
		createFile("/second/src/module-info.java",	secondFile);
		createFolder("/second/src/pack1");
		createFile("/second/src/pack1/I22.java",
				"package pack22;\n" +
				"public interface I22 {}\n");

		addClasspathEntry(project1, JavaCore.newProjectEntry(project2.getPath()));
		project1.close(); // sync
		project2.close();
		project2.open(null);
		project1.open(null);

		SearchPattern pattern = SearchPattern.createPattern("first.test.org", IJavaSearchConstants.MODULE, REFERENCES, ERASURE_RULE);
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaProject[] {getJavaProject("JavaSearchBugs9")});
		search(pattern, scope, this.resultCollector);

		assertSearchResults(
			"src/module-info.java second [first.test.org] EXACT_MATCH",
			this.resultCollector);
	}
	finally {
		deleteProject("JavaSearchBugs9");
		deleteProject("second");
	}
}
public void testBug501162_008() throws Exception {
	try {

		IJavaProject project1 = createJavaProject("JavaSearchBugs9", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project1.open(null);
		addClasspathEntry(project1, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String fileContent = 
			"module first {\n" +
			"    requires second;" +
			"    provides pack22.I22 with pack1.X11;" +
			"}\n";
		createFile("/JavaSearchBugs9/src/module-info.java",	fileContent);
		createFolder("/JavaSearchBugs9/src/pack1");
		createFile("/JavaSearchBugs9/src/pack1/X11.java",
				"package pack1;\n" +
				"public class X11 implements pack22.I22{}\n");

		IJavaProject project2 = createJavaProject("second", new String[] {"src"}, new String[] {"JCL18_LIB"}, "bin", "9");
		project2.open(null);
		addClasspathEntry(project2, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.MODULE_PATH")));
		String secondFile = 
				"module second {\n" +
				"    exports pack22 to first;\n" +
				"}\n";
		createFile("/second/src/module-info.java",	secondFile);
		createFolder("/second/src/pack1");
		createFile("/second/src/pack1/I22.java",
				"package pack22;\n" +
				"public interface I22 {}\n");

		addClasspathEntry(project1, JavaCore.newProjectEntry(project2.getPath()));
		project1.close(); // sync
		project2.close();
		project2.open(null);
		project1.open(null);

		SearchPattern pattern = SearchPattern.createPattern("second", IJavaSearchConstants.MODULE, REFERENCES, ERASURE_RULE);
		IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
		search(pattern, scope, this.resultCollector);

		assertSearchResults(
			"src/module-info.java first [second] EXACT_MATCH",
			this.resultCollector);
	}
	finally {
		deleteProject("JavaSearchBugs9");
		deleteProject("second");
	}
}

// Add more tests here
}