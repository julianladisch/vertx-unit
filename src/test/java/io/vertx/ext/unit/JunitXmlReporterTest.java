package io.vertx.ext.unit;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.impl.EventBusAdapterImpl;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JunitXmlReporterTest extends AsyncTestBase {

  @org.junit.Test
  public void testReportTestCases() {
    String testSuiteName = TestUtils.randomAlphaString(10);
    String testCaseName1 = TestUtils.randomAlphaString(10);
    String testCaseName2 = TestUtils.randomAlphaString(10);
    String testCaseName3 = TestUtils.randomAlphaString(10);

    TestSuite suite = TestSuite.create(testSuiteName).
        test(testCaseName1, test -> {}).
        test(testCaseName2, test -> { test.fail("the_assertion_failure"); }).
        test(testCaseName3, test -> { throw new RuntimeException("the_error_failure"); });

    Reporter reporter = Reporter.junitXmlReporter(buffer -> {
      Document doc = assertDoc(buffer);
      Element testsuiteElt = doc.getDocumentElement();
      assertEquals("testsuite", testsuiteElt.getTagName());
      assertNotNull(testsuiteElt.getAttribute("time"));
      assertEquals("3", testsuiteElt.getAttribute("tests"));
      assertEquals("1", testsuiteElt.getAttribute("errors"));
      assertEquals("1", testsuiteElt.getAttribute("failures"));
      assertEquals("0", testsuiteElt.getAttribute("skipped"));
      assertEquals(testSuiteName, testsuiteElt.getAttribute("name"));
      NodeList testCases = testsuiteElt.getElementsByTagName("testcase");
      assertEquals(3, testCases.getLength());
      Element testCase1Elt = (Element) testCases.item(0);
      assertEquals(testCaseName1, testCase1Elt.getAttribute("name"));
      assertNotNull(testCase1Elt.getAttribute("time"));
      assertEquals(0, testCase1Elt.getElementsByTagName("failure").getLength());
      Element testCase2Elt = (Element) testCases.item(1);
      assertEquals(testCaseName2, testCase2Elt.getAttribute("name"));
      assertNotNull(testCase2Elt.getAttribute("time"));
      assertEquals(1, testCase2Elt.getElementsByTagName("failure").getLength());
      Element testCase2FailureElt = (Element) testCase2Elt.getElementsByTagName("failure").item(0);
      assertEquals("AssertionError", testCase2FailureElt.getAttribute("type"));
      assertEquals("the_assertion_failure", testCase2FailureElt.getAttribute("message"));
      Element testCase3Elt = (Element) testCases.item(2);
      assertEquals(testCaseName3, testCase3Elt.getAttribute("name"));
      assertNotNull(testCase3Elt.getAttribute("time"));
      assertEquals(1, testCase3Elt.getElementsByTagName("failure").getLength());
      Element testCase3FailureElt = (Element) testCase3Elt.getElementsByTagName("failure").item(0);
      assertEquals("Error", testCase3FailureElt.getAttribute("type"));
      assertEquals("the_error_failure", testCase3FailureElt.getAttribute("message"));
      testComplete();
    });
    suite.run(reporter);
    await();

  }

  @org.junit.Test
  public void testReportAfterFailure() {
    String testSuiteName = TestUtils.randomAlphaString(10);
    String testCaseName1 = TestUtils.randomAlphaString(10);

    TestSuite suite = TestSuite.create(testSuiteName).
        test(testCaseName1, test -> {
        }).
        after(test -> {
          test.fail("the_after_failure");
        });

    Reporter reporter = Reporter.junitXmlReporter(buffer -> {
      Document doc = assertDoc(buffer);
      Element testsuiteElt = doc.getDocumentElement();
      assertEquals("testsuite", testsuiteElt.getTagName());
      assertNotNull(testsuiteElt.getAttribute("time"));
      assertEquals("2", testsuiteElt.getAttribute("tests"));
      assertEquals("1", testsuiteElt.getAttribute("errors"));
      assertEquals("0", testsuiteElt.getAttribute("skipped"));
      assertEquals(testSuiteName, testsuiteElt.getAttribute("name"));
      NodeList testCases = testsuiteElt.getElementsByTagName("testcase");
      assertEquals(2, testCases.getLength());
      Element testCase1Elt = (Element) testCases.item(0);
      assertEquals(testCaseName1, testCase1Elt.getAttribute("name"));
      assertNotNull(testCase1Elt.getAttribute("time"));
      assertEquals(0, testCase1Elt.getElementsByTagName("failure").getLength());
      Element testCase2Elt = (Element) testCases.item(1);
      assertEquals(testSuiteName, testCase2Elt.getAttribute("name"));
      assertNotNull(testCase2Elt.getAttribute("time"));
      assertEquals(1, testCase2Elt.getElementsByTagName("failure").getLength());
      Element testCase2FailureElt = (Element) testCase2Elt.getElementsByTagName("failure").item(0);
      assertEquals("AssertionError", testCase2FailureElt.getAttribute("type"));
      assertEquals("the_after_failure", testCase2FailureElt.getAttribute("message"));
      testComplete();
    });
    suite.run(reporter);
    await();
  }

  @org.junit.Test
  public void testReportBeforeFailure() {
    String testSuiteName = TestUtils.randomAlphaString(10);
    String testCaseName1 = TestUtils.randomAlphaString(10);

    TestSuite suite = TestSuite.create(testSuiteName).
        test(testCaseName1, test -> {
        }).
        before(test -> {
          test.fail("the_before_failure");
        });

    Reporter reporter = Reporter.junitXmlReporter(buffer -> {
      Document doc = assertDoc(buffer);
      Element testsuiteElt = doc.getDocumentElement();
      assertEquals("testsuite", testsuiteElt.getTagName());
      assertNotNull(testsuiteElt.getAttribute("time"));
      assertEquals("1", testsuiteElt.getAttribute("tests"));
      assertEquals("1", testsuiteElt.getAttribute("errors"));
      assertEquals("0", testsuiteElt.getAttribute("skipped"));
      assertEquals(testSuiteName, testsuiteElt.getAttribute("name"));
      NodeList testCases = testsuiteElt.getElementsByTagName("testcase");
      assertEquals(1, testCases.getLength());
      Element testCase1Elt = (Element) testCases.item(0);
      assertEquals(testSuiteName, testCase1Elt.getAttribute("name"));
      assertNotNull(testCase1Elt.getAttribute("time"));
      assertEquals(1, testCase1Elt.getElementsByTagName("failure").getLength());
      Element testCase2FailureElt = (Element) testCase1Elt.getElementsByTagName("failure").item(0);
      assertEquals("AssertionError", testCase2FailureElt.getAttribute("type"));
      assertEquals("the_before_failure", testCase2FailureElt.getAttribute("message"));
      testComplete();
    });
    suite.run(reporter);
    await();
  }

  @org.junit.Test
  public void testFromEventBus() {
    EventBusAdapter runner = new EventBusAdapterImpl();
    Vertx vertx = Vertx.vertx();
    runner.handler(Reporter.junitXmlReporter(buffer -> {
      Document doc = assertDoc(buffer);
      Element testsuiteElt = doc.getDocumentElement();
      assertEquals("testsuite", testsuiteElt.getTagName());
      NodeList testCases = testsuiteElt.getElementsByTagName("testcase");
      assertEquals(1, testCases.getLength());
      testComplete();
    }));
    vertx.eventBus().consumer("foobar", runner);
    TestSuite suite = TestSuite.create("my_suite").test("my_test", test -> {});
    suite.run(vertx, Reporter.eventBusReporter(vertx.eventBus().publisher("foobar")));
    await();
  }

  private Document assertDoc(Buffer buffer) {
    try {
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      return builder.parse(new ByteArrayInputStream(buffer.getBytes()));
    } catch (Exception e) {
      fail(e.getMessage());
      return null;
    }
  }
}
