/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.core.search;

import java.util.ArrayList;
import java.util.HashSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.compiler.env.AccessRuleSet;
import org.eclipse.jdt.internal.core.*;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.util.Util;

/**
 * A Java-specific scope for searching relative to one or more java elements.
 */
public class JavaSearchScope extends AbstractSearchScope {
	
	private ArrayList elements;

	/* The paths of the resources in this search scope 
	   (or the classpath entries' paths 
	   if the resources are projects) */
	private String[] paths;
	private boolean[] pathWithSubFolders;
	protected AccessRuleSet[] pathRestrictions;
	private String[] containerPaths;
	private int pathsCount;
	private int threshold;
	
	private IPath[] enclosingProjectsAndJars;
	public final static AccessRuleSet NOT_ENCLOSED = new AccessRuleSet(null);
	
public JavaSearchScope() {
	this(5);
}

private JavaSearchScope(int size) {
	initialize(size);
	
	//disabled for now as this could be expensive
	//JavaModelManager.getJavaModelManager().rememberScope(this);
}
	
private void addEnclosingProjectOrJar(IPath path) {
	int length = this.enclosingProjectsAndJars.length;
	for (int i = 0; i < length; i++) {
		if (this.enclosingProjectsAndJars[i].equals(path)) return;
	}
	System.arraycopy(
		this.enclosingProjectsAndJars,
		0,
		this.enclosingProjectsAndJars = new IPath[length+1],
		0,
		length);
	this.enclosingProjectsAndJars[length] = path;
}

/**
 * Add java project all fragment roots to current java search scope.
 * @see #add(JavaProject, IPath, int, HashSet, IClasspathEntry)
 */
public void add(JavaProject project, int includeMask, HashSet visitedProject) throws JavaModelException {
	add(project, null, includeMask, visitedProject, null);
}
/**
 * Add a path to current java search scope or all project fragment roots if null.
 * Use project resolved classpath to retrieve and store access restriction on each classpath entry.
 * Recurse if dependent projects are found.
 * @param javaProject Project used to get resolved classpath entries
 * @param pathToAdd Path to add in case of single element or null if user want to add all project package fragment roots
 * @param includeMask Mask to apply on classpath entries
 * @param visitedProjects Set to avoid infinite recursion
 * @param referringEntry Project raw entry in referring project classpath
 * @throws JavaModelException May happen while getting java model info 
 */
void add(JavaProject javaProject, IPath pathToAdd, int includeMask, HashSet visitedProjects, IClasspathEntry referringEntry) throws JavaModelException {
	IProject project = javaProject.getProject();
	if (!project.isAccessible() || !visitedProjects.add(project)) return;

	IPath projectPath = project.getFullPath();
	String projectPathString = projectPath.toString();
	this.addEnclosingProjectOrJar(projectPath);

	IClasspathEntry[] entries = javaProject.getResolvedClasspath(true/*ignoreUnresolvedEntry*/, false/*don't generateMarkerOnError*/, false/*don't returnResolutionInProgress*/);
	IJavaModel model = javaProject.getJavaModel();
	JavaModelManager.PerProjectInfo perProjectInfo = javaProject.getPerProjectInfo();
	for (int i = 0, length = entries.length; i < length; i++) {
		IClasspathEntry entry = entries[i];
		IClasspathEntry rawEntry = null;
		if (perProjectInfo != null && perProjectInfo.resolvedPathToRawEntries != null) {
			rawEntry = (IClasspathEntry) perProjectInfo.resolvedPathToRawEntries.get(entry.getPath());
		}
		if (rawEntry == null) continue;
		AccessRuleSet access = null;
		ClasspathEntry cpEntry = (ClasspathEntry) rawEntry;
		if (referringEntry != null) {
			cpEntry = cpEntry.combineWith((ClasspathEntry)referringEntry);
//				cpEntry = ((ClasspathEntry)referringEntry).combineWith(cpEntry);
		}
		access = cpEntry.getAccessRuleSet();
		switch (entry.getEntryKind()) {
			case IClasspathEntry.CPE_LIBRARY:
				switch (rawEntry.getEntryKind()) {
					case IClasspathEntry.CPE_LIBRARY:
					case IClasspathEntry.CPE_VARIABLE:
						if ((includeMask & APPLICATION_LIBRARIES) != 0) {
							IPath path = entry.getPath();
							if (pathToAdd == null || pathToAdd.equals(path)) {
								String pathString = path.toString();
								add(pathString, pathString, true, access);
								addEnclosingProjectOrJar(path);
							}
						}
						break;
					case IClasspathEntry.CPE_CONTAINER:
						IClasspathContainer container = JavaCore.getClasspathContainer(rawEntry.getPath(), javaProject);
						if (container == null) break;
						if ((container.getKind() == IClasspathContainer.K_APPLICATION && (includeMask & APPLICATION_LIBRARIES) != 0)
								|| (includeMask & SYSTEM_LIBRARIES) != 0) {
							IPath path = entry.getPath();
							if (pathToAdd == null || pathToAdd.equals(path)) {
								String pathString = path.toString();
								add(pathString, pathString, true, access);
								addEnclosingProjectOrJar(path);
							}
						}
						break;
				}
				break;
			case IClasspathEntry.CPE_PROJECT:
				if ((includeMask & REFERENCED_PROJECTS) != 0) {
					IPath path = entry.getPath();
					if (pathToAdd == null || pathToAdd.equals(path)) {
						add((JavaProject) model.getJavaProject(entry.getPath().lastSegment()), null, includeMask, visitedProjects, cpEntry);
					}
				}
				break;
			case IClasspathEntry.CPE_SOURCE:
				if ((includeMask & SOURCES) != 0) {
					IPath path = entry.getPath();
					if (pathToAdd == null || pathToAdd.equals(path)) {
						add(path.toString(), projectPathString, true, access);
					}
				}
				break;
		}
	}
}
/**
 * Add an element to the java search scope.
 * @param element The element we want to add to current java search scope
 * @throws JavaModelException May happen if some Java Model info are not available
 */
public void add(IJavaElement element) throws JavaModelException {
	IPath containerPath = null;
	int includeMask = SOURCES | APPLICATION_LIBRARIES | SYSTEM_LIBRARIES;
	switch (element.getElementType()) {
		case IJavaElement.JAVA_MODEL:
			// a workspace sope should be used
			break; 
		case IJavaElement.JAVA_PROJECT:
			add((JavaProject)element, null, includeMask, new HashSet(2), null);
			break;
		case IJavaElement.PACKAGE_FRAGMENT_ROOT:
			IPackageFragmentRoot root = (IPackageFragmentRoot)element;
			IPath rootPath = root.getPath();
			containerPath = root.getKind() == IPackageFragmentRoot.K_SOURCE ? root.getParent().getPath() : rootPath;
			add(rootPath.toString(), containerPath.toString(), true, null);
			break;
		case IJavaElement.PACKAGE_FRAGMENT:
			root = (IPackageFragmentRoot)element.getParent();
			if (root.isArchive()) {
				String relativePath = Util.concatWith(((PackageFragment) element).names, '/');
				containerPath = root.getPath();
				IPath path = containerPath.append(new Path(relativePath));
				add(path.toString(), containerPath.toString(), false, null);
			} else {
				IResource resource = element.getResource();
				if (resource != null && resource.isAccessible()) {
					containerPath = root.getKind() == IPackageFragmentRoot.K_SOURCE ? root.getParent().getPath() : root.getPath();
					add(resource.getFullPath().toString(), containerPath.toString(), false, null);
				}
			}
			break;
		default:
			// remember sub-cu (or sub-class file) java elements
			if (element instanceof IMember) {
				if (this.elements == null) {
					this.elements = new ArrayList();
				}
				this.elements.add(element);
			}
			root = (IPackageFragmentRoot) element.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			containerPath = root.getKind() == IPackageFragmentRoot.K_SOURCE ? root.getParent().getPath() : root.getPath();
			add(fullPath(element), containerPath.toString(), true, null);
	}
	
	if (containerPath != null)
		addEnclosingProjectOrJar(containerPath);
}

/**
 * Adds the given path to this search scope. Remember if subfolders need to be included
 * and associated access restriction as well.
 */
private void add(String path, String containerPath, boolean withSubFolders, AccessRuleSet access) {
	int index = (containerPath.hashCode() & 0x7FFFFFFF) % this.paths.length;
	String currentPath, currentContainerPath;
	while ((currentPath = this.paths[index]) != null && (currentContainerPath = this.containerPaths[index]) != null) {
		if (currentPath.equals(path) && currentContainerPath.equals(containerPath))
			return;
		index = (index + 1) % this.paths.length;
	}
	this.paths[index] = path;
	this.containerPaths[index] = containerPath;
	this.pathWithSubFolders[index] = withSubFolders;
	if (this.pathRestrictions != null)
		this.pathRestrictions[index] = access;
	else if (access != null) {
		this.pathRestrictions = new AccessRuleSet[this.paths.length];
		this.pathRestrictions[index] = access;
	}

	// assumes the threshold is never equal to the size of the table
	if (++this.pathsCount > this.threshold)
		rehash();
		
}

/* (non-Javadoc)
 * @see IJavaSearchScope#encloses(String)
 */
public boolean encloses(String resourcePathString) {
	return indexOf(fullPath(resourcePathString), null) >= 0;
}
private String fullPath(String resourcePathString) {
	int separatorIndex = resourcePathString.indexOf(JAR_FILE_ENTRY_SEPARATOR);
	if (separatorIndex != -1) {
		return resourcePathString.substring(0, separatorIndex).replace('\\', '/') + '/' + resourcePathString.substring(separatorIndex+1);
	}
	return resourcePathString;
}

/**
 * Returns paths list index of given path or -1 if not found.
 */
private int indexOf(String path, String containerPath) {
	if (containerPath != null) {
		// if container path is known, use the hash to get faster comparison
		int index = (containerPath.hashCode()& 0x7FFFFFFF) % this.paths.length;
		String currentContainerPath;
		while ((currentContainerPath = this.containerPaths[index]) != null) {
			if (currentContainerPath.equals(containerPath)) {
				String scopePath = this.paths[index];
				if (encloses(scopePath, path, index))
					return index;
			}
			index = (index + 1) % this.paths.length;
		}
		return -1;
	}
	
	// fallback to sequentially look at all known paths
	for (int i = 0, length = this.paths.length; i < length; i++) {
		String scopePath = this.paths[i];
		if (scopePath == null) continue;
		if (encloses(scopePath, path, i))
			return i;
	}
	return -1;
}

private boolean encloses(String scopePath, String path, int index) {
	if (this.pathWithSubFolders[index]) {
		if (path.startsWith(scopePath)) {
			return true;
		}
	} else {
		// if not looking at subfolders, this scope encloses the given path 
		// if this path is a direct child of the scope's ressource
		// or if this path is the scope's resource (see bug 13919 Declaration for package not found if scope is not project)
		if (path.startsWith(scopePath) 
			&& ((scopePath.length() == path.lastIndexOf('/'))
				|| (scopePath.length() == path.length()))) {
			return true;
		}
	}
	return false;
}

/* (non-Javadoc)
 * @see IJavaSearchScope#encloses(IJavaElement)
 */
public boolean encloses(IJavaElement element) {
	if (this.elements != null) {
		for (int i = 0, length = this.elements.size(); i < length; i++) {
			IJavaElement scopeElement = (IJavaElement)this.elements.get(i);
			IJavaElement searchedElement = element;
			while (searchedElement != null) {
				if (searchedElement.equals(scopeElement))
					return true;
				searchedElement = searchedElement.getParent();
			}
		}
		return false;
	}
	return this.indexOf(fullPath(element), null) >= 0;
}

/* (non-Javadoc)
 * @see IJavaSearchScope#enclosingProjectsAndJars()
 */
public IPath[] enclosingProjectsAndJars() {
	return this.enclosingProjectsAndJars;
}
private String fullPath(IJavaElement element) {
	if (element instanceof IPackageFragmentRoot) {
		return ((IPackageFragmentRoot)element).getPath().toString();
	}
	IJavaElement parent = element.getParent();
	String parentPath = parent == null ? null : fullPath(parent);
	String childPath;
	if (element instanceof PackageFragment) {
		String relativePath = Util.concatWith(((PackageFragment) element).names, '/');
		childPath = relativePath;
	} else if (element instanceof IOpenable) {
		childPath = element.getElementName();
	} else {
		return parentPath;
	}
	return parentPath == null ? childPath : parentPath + '/' + childPath;
}

/**
 * Get access rule set corresponding to a given path.
 * @param path The path user want to have restriction access
 * @return The access rule set for given path or null if none is set for it.
 * 	Returns specific uninit access rule set when scope does not enclose the given path.
 */
public AccessRuleSet getAccessRuleSet(String path, String containerPath) {
	int index = indexOf(fullPath(path), containerPath);
	if (index == -1) {
		// this search scope does not enclose given path
		return NOT_ENCLOSED;
	}
	if (this.pathRestrictions == null)
		return null;
	return this.pathRestrictions[index];
}

protected void initialize(int size) {
	this.pathsCount = 0;
	this.threshold = size; // size represents the expected number of elements
	int extraRoom = (int) (size * 1.75f);
	if (this.threshold == extraRoom)
		extraRoom++;
	this.paths = new String[extraRoom];
	this.containerPaths = new String[extraRoom];
	this.pathWithSubFolders = new boolean[extraRoom];
	this.pathRestrictions = null; // null to optimize case where no access rules are used

	this.enclosingProjectsAndJars = new IPath[0];
}
/*
 * @see AbstractSearchScope#processDelta(IJavaElementDelta)
 */
public void processDelta(IJavaElementDelta delta) {
	switch (delta.getKind()) {
		case IJavaElementDelta.CHANGED:
			IJavaElementDelta[] children = delta.getAffectedChildren();
			for (int i = 0, length = children.length; i < length; i++) {
				IJavaElementDelta child = children[i];
				this.processDelta(child);
			}
			break;
		case IJavaElementDelta.REMOVED:
			IJavaElement element = delta.getElement();
			if (this.encloses(element)) {
				if (this.elements != null) {
					this.elements.remove(element);
				} 
				IPath path = null;
				switch (element.getElementType()) {
					case IJavaElement.JAVA_PROJECT:
						path = ((IJavaProject)element).getProject().getFullPath();
					case IJavaElement.PACKAGE_FRAGMENT_ROOT:
						if (path == null) {
							path = ((IPackageFragmentRoot)element).getPath();
						}
						int toRemove = -1;
						for (int i = 0; i < this.pathsCount; i++) {
							if (this.paths[i].equals(path)) {
								toRemove = i;
								break;
							}
						}
						if (toRemove != -1) {
							this.paths[toRemove] = null;
							rehash();
						}
				}
			}
			break;
	}
}

private void rehash() {
	JavaSearchScope newScope = new JavaSearchScope(this.pathsCount * 2);		// double the number of expected elements
	String currentPath;
	for (int i = this.paths.length; --i >= 0;)
		if ((currentPath = this.paths[i]) != null)
			newScope.add(currentPath, this.containerPaths[i], this.pathWithSubFolders[i], this.pathRestrictions == null ? null : this.pathRestrictions[i]);

	this.paths = newScope.paths;
	this.containerPaths = newScope.containerPaths;
	this.pathWithSubFolders = newScope.pathWithSubFolders;
	this.pathRestrictions = newScope.pathRestrictions;
	this.threshold = newScope.threshold;
}

public String toString() {
	StringBuffer result = new StringBuffer("JavaSearchScope on "); //$NON-NLS-1$
	if (this.elements != null) {
		result.append("["); //$NON-NLS-1$
		for (int i = 0, length = this.elements.size(); i < length; i++) {
			JavaElement element = (JavaElement)this.elements.get(i);
			result.append("\n\t"); //$NON-NLS-1$
			result.append(element.toStringWithAncestors());
		}
		result.append("\n]"); //$NON-NLS-1$
	} else {
		if (this.pathsCount == 0) {
			result.append("[empty scope]"); //$NON-NLS-1$
		} else {
			result.append("["); //$NON-NLS-1$
			for (int i = 0; i < this.paths.length; i++) {
				String path = this.paths[i];
				if (path == null) continue;
				result.append("\n\t"); //$NON-NLS-1$
				result.append(path);
			}
			result.append("\n]"); //$NON-NLS-1$
		}
	}
	return result.toString();
}
}
