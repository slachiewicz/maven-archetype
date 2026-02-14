/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.archetype.ui.generation;

import javax.inject.Inject;

import java.util.regex.Pattern;

import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.testing.PlexusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

@PlexusTest
public class DefaultArchetypeGenerationQueryerTest {

    @Inject
    private DefaultArchetypeGenerationQueryer queryer;

    @Test
    public void testPropertyRegexValidationRetry() throws PrompterException {
        Prompter prompter = Mockito.mock(Prompter.class);

        Mockito.when(prompter.prompt(Mockito.any())).thenReturn("invalid-answer");
        Mockito.when(prompter.prompt(Mockito.any())).thenReturn("valid-answer");
        queryer.setPrompter(prompter);

        String value = queryer.getPropertyValue("custom-property", null, Pattern.compile("^valid-.*"));

        assertEquals("valid-answer", value);
    }
}
