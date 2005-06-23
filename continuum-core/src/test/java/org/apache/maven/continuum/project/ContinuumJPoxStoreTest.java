package org.apache.maven.continuum.project;

/*
 * Copyright 2004-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.jdo.JDODetachedFieldAccessException;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;

import org.apache.maven.continuum.scm.CheckOutScmResult;
import org.apache.maven.continuum.scm.ScmFile;

import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.jdo.JdoFactory;

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class ContinuumJPoxStoreTest
    extends PlexusTestCase
{
    private JdoFactory jdoFactory;

    private PersistenceManagerFactory pmf;

    private ContinuumJPoxStore store;

    public void setUp()
        throws Exception
    {
        super.setUp();

        jdoFactory = (JdoFactory) lookup( JdoFactory.ROLE );

        pmf = jdoFactory.getPersistenceManagerFactory();

        store = new ContinuumJPoxStore( pmf );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        Collection projects = store.getContinuumProjectCollection( true, "", "" );

        for ( Iterator it = projects.iterator(); it.hasNext(); )
        {
            ContinuumProject project = (ContinuumProject) it.next();

            store.deleteContinuumProject( project.getId() );
        }
    }

    public void testNotifiers()
        throws Exception
    {
        // ----------------------------------------------------------------------
        // Store a single notifier
        // ----------------------------------------------------------------------

        ContinuumNotifier n = new ContinuumNotifier();

        n.setType( "foo" );

        n.getConfiguration().put( "foo", "bar" );

        Object oid = store.addContinuumNotifier( n );

        n = store.getContinuumNotifierByJdoId( oid, true );

        assertEquals( "foo", n.getType() );

        assertNotNull( n.getConfiguration() );

        assertEquals( 1, n.getConfiguration().size() );

        assertEquals( "bar", n.getConfiguration().get( "foo" ) );

        // ----------------------------------------------------------------------
        // Update a single notifier
        // ----------------------------------------------------------------------

        n = store.getContinuumNotifierByJdoId( oid, true );

        n.setType( "bar" );

        n.getConfiguration().remove( "foo" );

        n.getConfiguration().put( "bar", "foo" );

        PersistenceManager pm = store.begin();

        pm.attachCopy( n, true );

        store.commit();

        n = store.getContinuumNotifierByJdoId( oid, true );

        assertNotifier( "bar", "bar", "foo", n );

        assertEquals( 1, n.getConfiguration().size() );
    }

    public void testNotifiersInProject()
        throws Exception
    {
        // ----------------------------------------------------------------------
        // Make a project with two notifiers
        // ----------------------------------------------------------------------

        ContinuumProject p = makeProject( store );

        ContinuumNotifier n;

        n = makeNotifier( "foo", "foo", "bar" );

        p.getNotifiers().add( n );

        n = makeNotifier( "bar", "bar", "foo" );

        p.getNotifiers().add( n );

        store.storeContinuumProject( p );

        // ----------------------------------------------------------------------
        // Assert
        // ----------------------------------------------------------------------

        p = store.getContinuumProject( p.getId(), true );

        List notifiers = p.getNotifiers();

        assertEquals( 2, notifiers.size() );

        assertNotifier( "foo", "foo", "bar", (ContinuumNotifier)notifiers.get( 0 ) );

        assertNotifier( "bar", "bar", "foo", (ContinuumNotifier) notifiers.get( 1 ) );

        // ----------------------------------------------------------------------
        // Modify the first notifier
        // ----------------------------------------------------------------------

        p = store.getContinuumProject( p.getId(), true );

        notifiers = p.getNotifiers();

        n = (ContinuumNotifier) notifiers.get( 0 );

        n.setType( "baz" );

        // change a existsing property
        n.getConfiguration().put( "foo", "foo" );

        // add another property
        n.getConfiguration().put( "baz", "yay" );

        store.storeContinuumProject( p );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        p = store.getContinuumProject( p.getId(), true );

        notifiers = p.getNotifiers();

        assertEquals( 2, notifiers.size() );

        n = (ContinuumNotifier) notifiers.get( 0 );

        assertEquals( "baz", n.getType() );

        assertNotNull( n.getConfiguration() );

        assertEquals( "foo", n.getConfiguration().get( "foo" ) );

        assertEquals( "yay", n.getConfiguration().get( "baz" ) );

        n = (ContinuumNotifier) notifiers.get( 1 );

        assertEquals( "bar", n.getType() );

        assertNotNull( n.getConfiguration() );

        assertEquals( "foo", n.getConfiguration().get( "bar" ) );
    }

    public void testCascadingDelete()
        throws Exception
    {
        JdoFactory jdoFactory = (JdoFactory) lookup( JdoFactory.ROLE );

        PersistenceManagerFactory pmf = jdoFactory.getPersistenceManagerFactory();

        ContinuumJPoxStore store = new ContinuumJPoxStore( pmf );

        ContinuumProject p;

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        makeProject( store );

        // ----------------------------------------------------------------------
        // Assert that the objects are there
        // ----------------------------------------------------------------------

        Collection projects = store.getContinuumProjectCollection( true, "", "" );

        assertEquals( 1, projects.size() );

        p = (ContinuumProject) projects.iterator().next();

        Collection builds = p.getBuilds();

        assertEquals( 2, builds.size() );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        store.deleteContinuumProject( p.getId() );

        assertEquals( 0, store.getContinuumProjectCollection( true, "", "" ).size() );

        assertEquals( 0, store.getContinuumBuildCollection( true, "", "" ).size() );

        assertEquals( 0, store.getCheckOutScmResultCollection( true, "", "" ).size() );
    }

    public void testFetchGroups()
        throws Exception
    {
        ContinuumProject p = makeProject( store );

        String projectId = p.getId();

        // ----------------------------------------------------------------------
        // Try to get a single project. This object should include the
        // "detailed" fetch group so it should be possible to access all
        // collections.
        // ----------------------------------------------------------------------

        p = store.getContinuumProject( projectId, true );

        assertEquals( "check out error exception", p.getCheckOutErrorException() );

        p.getCheckOutScmResult();

        p.getBuilds();

        p.getDevelopers();

        // ----------------------------------------------------------------------
        // Get a project from a Collection query and assert that it only
        // includes the summary part
        // ----------------------------------------------------------------------

        Collection projects = store.getContinuumProjectCollection( true, "", "" );

        assertEquals( "projects.size", 1 , projects.size() );

        p = (ContinuumProject) projects.iterator().next();

        assertEquals( "project.id", projectId, p.getId() );

        assertEquals( "project.checkOutException",
                      "check out error exception", p.getCheckOutErrorException() );

        // ----------------------------------------------------------------------
        // This is a 1..1 association
        // ----------------------------------------------------------------------

        try
        {
            p.getCheckOutScmResult();

            fail( "Expected a JDODetachedFieldAccessException." );
        }
        catch ( JDODetachedFieldAccessException e )
        {
            // expected
        }

        // ----------------------------------------------------------------------
        // This is a 1..n association
        // ----------------------------------------------------------------------

//        try
//        {
//            p.getDevelopers();
//
//            fail( "Expected a JDODetachedFieldAccessException." );
//        }
//        catch ( JDODetachedFieldAccessException e )
//        {
//            // expected
//        }
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    private void assertNotifier( String type, String key, String value, ContinuumNotifier notifier )
    {
        assertEquals( type, notifier.getType() );

        assertNotNull( notifier.getConfiguration() );

        assertEquals( 1, notifier.getConfiguration().size() );

        assertEquals( value, notifier.getConfiguration().get( key ) );
    }

    private ContinuumNotifier makeNotifier( String type, String key, String value )
    {
        ContinuumNotifier notifier = new ContinuumNotifier();

        notifier.setType( type );

        notifier.getConfiguration().put( key, value );

        return notifier;
    }

    private ContinuumProject makeProject( ContinuumJPoxStore store )
        throws Exception
    {
        ContinuumProject p;

        ContinuumBuild build;

        p = new MavenTwoProject();

        p.setName( "Yo Yo Project" );

        Object oid = store.storeContinuumProject( p );

        p = store.getContinuumProjectByJdoId( oid, true );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        CheckOutScmResult result = new CheckOutScmResult();

        result.setCommandOutput( "command output" );

        result.setProviderMessage( "provider message" );

        result.setSuccess( true );

        ScmFile scmFile = new ScmFile();

        scmFile.setPath( "/foo" );

        result.getCheckedOutFiles().add( scmFile );

        p.setCheckOutScmResult( result );

        p.setCheckOutErrorException( "check out error exception" );

        // ----------------------------------------------------------------------
        // Make two builds in the project
        // ----------------------------------------------------------------------

        build = new ContinuumBuild();

        build.setSuccess( true );

        build.setExitCode( 1 );

        build.setProject( p );

        build = new ContinuumBuild();

        build.setSuccess( true );

        build.setExitCode( 2 );

        build.setProject( p );

        store.storeContinuumProject( p );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        return store.getContinuumProjectByJdoId( oid, true );
    }
}
