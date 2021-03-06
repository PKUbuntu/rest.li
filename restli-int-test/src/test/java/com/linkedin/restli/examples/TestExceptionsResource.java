/*
   Copyright (c) 2012 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.restli.examples;

import com.linkedin.restli.client.BatchCreateRequest;
import com.linkedin.restli.client.CreateRequest;
import com.linkedin.restli.client.ErrorHandlingBehavior;
import com.linkedin.restli.client.Response;
import com.linkedin.restli.client.ResponseFuture;
import com.linkedin.restli.common.CollectionResponse;
import com.linkedin.restli.common.CreateStatus;
import com.linkedin.restli.common.EmptyRecord;
import com.linkedin.restli.common.ErrorResponse;
import com.linkedin.restli.examples.greetings.api.Tone;
import java.util.Collections;

import java.util.List;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.linkedin.r2.RemoteInvocationException;
import com.linkedin.r2.transport.common.Client;
import com.linkedin.r2.transport.common.bridge.client.TransportClientAdapter;
import com.linkedin.r2.transport.http.client.HttpClientFactory;
import com.linkedin.restli.client.GetRequest;
import com.linkedin.restli.client.RestClient;
import com.linkedin.restli.client.RestLiResponseException;
import com.linkedin.restli.common.HttpStatus;
import com.linkedin.restli.examples.greetings.api.Greeting;
import com.linkedin.restli.examples.greetings.client.ExceptionsBuilders;

public class TestExceptionsResource extends RestLiIntegrationTest
{
  private static final Client CLIENT = new TransportClientAdapter(new HttpClientFactory().getClient(Collections.<String, String>emptyMap()));
  private static final String URI_PREFIX = "http://localhost:1338/";
  private static final RestClient REST_CLIENT = new RestClient(CLIENT, URI_PREFIX);

  @BeforeClass
  public void initClass() throws Exception
  {
    super.init();
  }

  @AfterClass
  public void shutDown() throws Exception
  {
    super.shutdown();
  }

  @Test(dataProvider = "exceptionHandlingModes")
  public void testException(boolean explicit, ErrorHandlingBehavior errorHandlingBehavior) throws RemoteInvocationException
  {
    Response<Greeting> response = null;
    RestLiResponseException exception = null;

    try
    {
      GetRequest<Greeting> readRequest = new ExceptionsBuilders().get().id(1L).build();
      ResponseFuture<Greeting> future;

      if (explicit)
      {
        future = REST_CLIENT.sendRequest(readRequest, errorHandlingBehavior);
      }
      else
      {
        future = REST_CLIENT.sendRequest(readRequest);
      }

      response = future.getResponse();

      if (!explicit || errorHandlingBehavior == ErrorHandlingBehavior.FAIL_ON_ERROR)
      {
        Assert.fail("expected exception");
      }
    }
    catch (RestLiResponseException e)
    {
      if (!explicit || errorHandlingBehavior == ErrorHandlingBehavior.FAIL_ON_ERROR)
      {
        exception = e;
      }
      else
      {
        Assert.fail("not expected exception");
      }
    }

    if (explicit && errorHandlingBehavior == ErrorHandlingBehavior.TREAT_SERVER_ERROR_AS_SUCCESS)
    {
      Assert.assertNotNull(response);
      Assert.assertTrue(response.hasError());
      exception = response.getError();
      Assert.assertEquals(response.getStatus(), HttpStatus.S_500_INTERNAL_SERVER_ERROR.getCode());
      Assert.assertNull(response.getEntity());
    }

    Assert.assertNotNull(exception);
    Assert.assertFalse(exception.hasDecodedResponse());
    Assert.assertEquals(exception.getStatus(), HttpStatus.S_500_INTERNAL_SERVER_ERROR.getCode());
    Assert.assertEquals(exception.getServiceErrorCode(), 42);
    Assert.assertEquals(exception.getServiceErrorMessage(), "error processing request");
    Assert.assertTrue(exception.getServiceErrorStackTrace().contains(
        "at com.linkedin.restli.examples.greetings.server.ExceptionsResource.get("));
  }

  @Test(dataProvider = "exceptionHandlingModes")
  public void testCreateError(boolean explicit, ErrorHandlingBehavior errorHandlingBehavior) throws Exception
  {
    Response<EmptyRecord> response = null;
    RestLiResponseException exception = null;

    try
    {
      CreateRequest<Greeting> createRequest = new ExceptionsBuilders().create()
          .input(new Greeting().setId(11L).setMessage("@#$%@!$%").setTone(Tone.INSULTING))
          .build();
      ResponseFuture<EmptyRecord> future;

      if (explicit)
      {
        future = REST_CLIENT.sendRequest(createRequest, errorHandlingBehavior);
      }
      else
      {
        future = REST_CLIENT.sendRequest(createRequest);
      }

      response = future.getResponse();

      if (!explicit || errorHandlingBehavior == ErrorHandlingBehavior.FAIL_ON_ERROR)
      {
        Assert.fail("expected exception");
      }
    }
    catch (RestLiResponseException e)
    {
      if (!explicit || errorHandlingBehavior == ErrorHandlingBehavior.FAIL_ON_ERROR)
      {
        exception = e;
      }
      else
      {
        Assert.fail("not expected exception");
      }
    }

    if (explicit && errorHandlingBehavior == ErrorHandlingBehavior.TREAT_SERVER_ERROR_AS_SUCCESS)
    {
      Assert.assertNotNull(response);
      Assert.assertTrue(response.hasError());
      exception = response.getError();
      Assert.assertEquals(response.getStatus(), HttpStatus.S_406_NOT_ACCEPTABLE.getCode());
      Assert.assertNull(response.getEntity());
    }

    Assert.assertNotNull(exception);
    Assert.assertFalse(exception.hasDecodedResponse());
    Assert.assertEquals(exception.getStatus(), HttpStatus.S_406_NOT_ACCEPTABLE.getCode());
    Assert.assertEquals(exception.getServiceErrorMessage(), "I will not tolerate your insolence!");
    Assert.assertEquals(exception.getServiceErrorCode(), 999);
    Assert.assertEquals(exception.getErrorSource(), "APP");
    Assert.assertEquals(exception.getErrorDetails().getString("reason"), "insultingGreeting");
    Assert.assertTrue(exception.getServiceErrorStackTrace().startsWith(
        "com.linkedin.restli.server.RestLiServiceException [HTTP Status:406, serviceErrorCode:999]: I will not tolerate your insolence!"),
                      "stacktrace mismatch:" + exception.getStackTrace());
  }

  @Test
  public void testBatchCreateErrors() throws Exception
  {
    BatchCreateRequest<Greeting> batchCreateRequest = new ExceptionsBuilders().batchCreate()
        .input(new Greeting().setId(10L).setMessage("Greetings.").setTone(Tone.SINCERE))
        .input(new Greeting().setId(11L).setMessage("@#$%@!$%").setTone(Tone.INSULTING))
        .build();

    CollectionResponse<CreateStatus> response = REST_CLIENT.sendRequest(batchCreateRequest).getResponse().getEntity();
    List<CreateStatus> createStatuses = response.getElements();
    Assert.assertEquals(createStatuses.size(), 2);

    Assert.assertEquals(createStatuses.get(0).getStatus().intValue(), HttpStatus.S_201_CREATED.getCode());
    Assert.assertEquals(createStatuses.get(0).getId(), "10");
    Assert.assertFalse(createStatuses.get(0).hasError());

    CreateStatus status = createStatuses.get(1);
    Assert.assertEquals(status.getStatus().intValue(), HttpStatus.S_406_NOT_ACCEPTABLE.getCode());
    Assert.assertTrue(status.hasError());
    ErrorResponse error = status.getError();
    Assert.assertEquals(error.getStatus(), HttpStatus.S_406_NOT_ACCEPTABLE.getCode());
    Assert.assertEquals(error.getMessage(), "I will not tolerate your insolence!");
    Assert.assertEquals(error.getServiceErrorCode(), 999);
    Assert.assertEquals(error.getExceptionClass(), "com.linkedin.restli.server.RestLiServiceException");
    Assert.assertEquals(error.getErrorDetails().getString("reason"), "insultingGreeting");
    Assert.assertTrue(error.getStackTrace().startsWith(
        "com.linkedin.restli.server.RestLiServiceException [HTTP Status:406, serviceErrorCode:999]: I will not tolerate your insolence!"),
                      "stacktrace mismatch:" + error.getStackTrace());
  }

  @DataProvider(name = "exceptionHandlingModes")
  public Object[][] listFactories()
  {
    return new Object[][] {
        { true, ErrorHandlingBehavior.FAIL_ON_ERROR},
        { true, ErrorHandlingBehavior.TREAT_SERVER_ERROR_AS_SUCCESS },
        { false, null }
    };
  }
}
