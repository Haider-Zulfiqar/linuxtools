/*******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributor:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package org.eclipse.linuxtools.docker.integration.tests.container;

import static org.junit.Assert.assertTrue;

import org.eclipse.linuxtools.docker.integration.tests.image.AbstractImageBotTest;
import org.eclipse.linuxtools.docker.integration.tests.mock.MockUtils;
import org.eclipse.linuxtools.docker.reddeer.core.ui.wizards.ImageRunSelectionPage;
import org.jboss.reddeer.common.wait.TimePeriod;
import org.jboss.reddeer.common.wait.WaitWhile;
import org.jboss.reddeer.core.condition.JobIsRunning;
import org.jboss.reddeer.eclipse.condition.ConsoleHasNoChange;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author jkopriva@redhat.com
 * @contributor adietish@redhat.com
 *
 */

public class DockerContainerTest extends AbstractImageBotTest {

	private static final String IMAGE_NAME = IMAGE_BUSYBOX;
	private static final String CONTAINER_NAME = "test_run";

	@Before
	public void before() {
		clearConsole();
		deleteAllConnections();
		getConnection();
		pullImage(IMAGE_NAME);
	}

	@Test
	public void testRunDockerContainer() {
		new WaitWhile(new JobIsRunning(), TimePeriod.LONG);
		assertTrue("Image has not been found!", imageIsDeployed(getCompleteImageName(IMAGE_NAME)));
		getConnection().getImage(getCompleteImageName(IMAGE_NAME)).run();
		ImageRunSelectionPage firstPage = new ImageRunSelectionPage();
		firstPage.setContainerName(CONTAINER_NAME);
		firstPage.finish();
		if(mockitoIsUsed()){
			MockUtils.runContainer(DEFAULT_CONNECTION_NAME, IMAGE_NAME, IMAGE_TAG_LATEST, CONTAINER_NAME);
		}
		new WaitWhile(new JobIsRunning(), TimePeriod.NORMAL);
		new WaitWhile(new ConsoleHasNoChange());
		assertTrue("Container does not exists!",containerIsDeployed(CONTAINER_NAME));
	}

	@After
	public void after() {
		deleteImageContainerAfter(CONTAINER_NAME);
	}

}
