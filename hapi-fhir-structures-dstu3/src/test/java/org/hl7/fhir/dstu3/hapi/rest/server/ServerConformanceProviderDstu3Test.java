package org.hl7.fhir.dstu3.hapi.rest.server;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.dstu3.model.CodeType;
import org.hl7.fhir.dstu3.model.Conformance;
import org.hl7.fhir.dstu3.model.Conformance.ConditionalDeleteStatus;
import org.hl7.fhir.dstu3.model.Conformance.ConformanceRestComponent;
import org.hl7.fhir.dstu3.model.Conformance.ConformanceRestOperationComponent;
import org.hl7.fhir.dstu3.model.Conformance.ConformanceRestResourceComponent;
import org.hl7.fhir.dstu3.model.Conformance.ConformanceRestResourceSearchParamComponent;
import org.hl7.fhir.dstu3.model.Conformance.SystemRestfulInteraction;
import org.hl7.fhir.dstu3.model.Conformance.TypeRestfulInteraction;
import org.hl7.fhir.dstu3.model.DateType;
import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.OperationDefinition;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.Lists;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.History;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.IncludeParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.annotation.Validate;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.method.BaseMethodBinding;
import ca.uhn.fhir.rest.method.IParameter;
import ca.uhn.fhir.rest.method.SearchMethodBinding;
import ca.uhn.fhir.rest.method.SearchParameter;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.ResourceBinding;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.util.TestUtil;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;

@SuppressWarnings({"deprecation","unused"})
public class ServerConformanceProviderDstu3Test {

	private static FhirContext ourCtx;
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ServerConformanceProviderDstu3Test.class);
	private static FhirValidator ourValidator;

	static {
		ourCtx = FhirContext.forDstu3();
		ourValidator = ourCtx.newValidator();
		ourValidator.setValidateAgainstStandardSchema(true);
		ourValidator.setValidateAgainstStandardSchematron(true);
	}

	private HttpServletRequest createHttpServletRequest() {
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getRequestURI()).thenReturn("/FhirStorm/fhir/Patient/_search");
		when(req.getServletPath()).thenReturn("/fhir");
		when(req.getRequestURL()).thenReturn(new StringBuffer().append("http://fhirstorm.dyndns.org:8080/FhirStorm/fhir/Patient/_search"));
		when(req.getContextPath()).thenReturn("/FhirStorm");
		return req;
	}

	private ServletConfig createServletConfig() {
		ServletConfig sc = mock(ServletConfig.class);
		when(sc.getServletContext()).thenReturn(null);
		return sc;
	}

	private ConformanceRestResourceComponent findRestResource(Conformance conformance, String wantResource) throws Exception {
		ConformanceRestResourceComponent resource = null;
		for (ConformanceRestResourceComponent next : conformance.getRest().get(0).getResource()) {
			if (next.getType().equals(wantResource)) {
				resource = next;
			}
		}
		if (resource == null) {
			throw new Exception("Could not find resource: " + wantResource);
		}
		return resource;
	}

	@Test
	public void testConditionalOperations() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new ConditionalProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		ConformanceRestResourceComponent res = conformance.getRest().get(0).getResource().get(1);
		assertEquals("Patient", res.getType());

		assertTrue(res.getConditionalCreate());
		assertEquals(ConditionalDeleteStatus.MULTIPLE, res.getConditionalDelete());
		assertTrue(res.getConditionalUpdate());
	}

	@Test
	public void testExtendedOperationReturningBundle() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new ProviderWithExtendedOperationReturningBundle());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());

		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		assertEquals(1, conformance.getRest().get(0).getOperation().size());
		assertEquals("everything", conformance.getRest().get(0).getOperation().get(0).getName());

		OperationDefinition opDef = sc.readOperationDefinition(new IdType("OperationDefinition/Patient-i-everything"));
		validate(opDef);
		assertEquals("everything", opDef.getCode());
	}

	@Test
	public void testExtendedOperationReturningBundleOperation() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new ProviderWithExtendedOperationReturningBundle());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs) {
		};
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		OperationDefinition opDef = sc.readOperationDefinition(new IdType("OperationDefinition/Patient-i-everything"));
		validate(opDef);

		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(opDef);
		ourLog.info(conf);

		assertEquals("everything", opDef.getCode());
		assertEquals(true, opDef.getIdempotent());
	}

	@Test
	public void testInstanceHistorySupported() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new InstanceHistoryProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		conf = ourCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
		assertThat(conf, containsString("<interaction><code value=\"" + TypeRestfulInteraction.HISTORYINSTANCE.toCode() + "\"/></interaction>"));
	}

	@Test
	public void testMultiOptionalDocumentation() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new MultiOptionalProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		boolean found = false;
		Collection<ResourceBinding> resourceBindings = rs.getResourceBindings();
		for (ResourceBinding resourceBinding : resourceBindings) {
			if (resourceBinding.getResourceName().equals("Patient")) {
				List<BaseMethodBinding<?>> methodBindings = resourceBinding.getMethodBindings();
				SearchMethodBinding binding = (SearchMethodBinding) methodBindings.get(0);
				SearchParameter param = (SearchParameter) binding.getParameters().iterator().next();
				assertEquals("The patient's identifier", param.getDescription());
				found = true;
			}
		}

		assertTrue(found);
		Conformance conformance = sc.getServerConformance(createHttpServletRequest());
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		assertThat(conf, containsString("<documentation value=\"The patient's identifier\"/>"));
		assertThat(conf, containsString("<documentation value=\"The patient's name\"/>"));
		assertThat(conf, containsString("<type value=\"token\"/>"));
	}

	@Test
	public void testNonConditionalOperations() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new NonConditionalProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		ConformanceRestResourceComponent res = conformance.getRest().get(0).getResource().get(1);
		assertEquals("Patient", res.getType());

		assertNull(res.getConditionalCreateElement().getValue());
		assertNull(res.getConditionalDeleteElement().getValue());
		assertNull(res.getConditionalUpdateElement().getValue());
	}

	/** See #379 */
	@Test
	public void testOperationAcrossMultipleTypes() throws Exception {
		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new MultiTypePatientProvider(), new MultiTypeEncounterProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());

		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		assertEquals(4, conformance.getRest().get(0).getOperation().size());
		List<String> operationNames = toOperationNames(conformance.getRest().get(0).getOperation());
		assertThat(operationNames, containsInAnyOrder("someOp", "validate", "someOp", "validate"));

		List<String> operationIdParts = toOperationIdParts(conformance.getRest().get(0).getOperation());
		assertThat(operationIdParts, containsInAnyOrder("Patient-i-someOp", "Encounter-i-someOp", "Patient-i-validate", "Encounter-i-validate"));

		{
			OperationDefinition opDef = sc.readOperationDefinition(new IdType("OperationDefinition/Patient-i-someOp"));
			validate(opDef);
			ourLog.info(ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(opDef));
			Set<String> types = toStrings(opDef.getResource());
			assertEquals("someOp", opDef.getCode());
			assertEquals(true, opDef.getInstance());
			assertEquals(false, opDef.getSystem());
			assertThat(types, containsInAnyOrder("Patient"));
			assertEquals(2, opDef.getParameter().size());
			assertEquals("someOpParam1", opDef.getParameter().get(0).getName());
			assertEquals("date", opDef.getParameter().get(0).getType());
			assertEquals("someOpParam2", opDef.getParameter().get(1).getName());
			assertEquals("Patient", opDef.getParameter().get(1).getType());
		}
		{
			OperationDefinition opDef = sc.readOperationDefinition(new IdType("OperationDefinition/Encounter-i-someOp"));
			validate(opDef);
			ourLog.info(ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(opDef));
			Set<String> types = toStrings(opDef.getResource());
			assertEquals("someOp", opDef.getCode());
			assertEquals(true, opDef.getInstance());
			assertEquals(false, opDef.getSystem());
			assertThat(types, containsInAnyOrder("Encounter"));
			assertEquals(2, opDef.getParameter().size());
			assertEquals("someOpParam1", opDef.getParameter().get(0).getName());
			assertEquals("date", opDef.getParameter().get(0).getType());
			assertEquals("someOpParam2", opDef.getParameter().get(1).getName());
			assertEquals("Encounter", opDef.getParameter().get(1).getType());
		}
		{
			OperationDefinition opDef = sc.readOperationDefinition(new IdType("OperationDefinition/Patient-i-validate"));
			validate(opDef);
			ourLog.info(ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(opDef));
			Set<String> types = toStrings(opDef.getResource());
			assertEquals("validate", opDef.getCode());
			assertEquals(true, opDef.getInstance());
			assertEquals(false, opDef.getSystem());
			assertThat(types, containsInAnyOrder("Patient"));
			assertEquals(1, opDef.getParameter().size());
			assertEquals("resource", opDef.getParameter().get(0).getName());
			assertEquals("Patient", opDef.getParameter().get(0).getType());
		}
	}
	
	@Test
	public void testOperationDocumentation() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new SearchProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());

		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);

		assertThat(conf, containsString("<documentation value=\"The patient's identifier (MRN or other card number)\"/>"));
		assertThat(conf, containsString("<type value=\"token\"/>"));

	}

	@Test
	public void testOperationOnNoTypes() throws Exception {
		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new PlainProviderWithExtendedOperationOnNoType());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs) {
			@Override
			public Conformance getServerConformance(HttpServletRequest theRequest) {
				return super.getServerConformance(theRequest);
			}
		};
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		OperationDefinition opDef = sc.readOperationDefinition(new IdType("OperationDefinition/-is-plain"));
		validate(opDef);

		assertEquals("plain", opDef.getCode());
		assertEquals(true, opDef.getIdempotent());
		assertEquals(3, opDef.getParameter().size());

		assertTrue(opDef.getParameter().get(0).hasName());
		assertEquals("start", opDef.getParameter().get(0).getName());
		assertEquals("in", opDef.getParameter().get(0).getUse().toCode());
		assertEquals("0", opDef.getParameter().get(0).getMinElement().getValueAsString());
		assertEquals("date", opDef.getParameter().get(0).getTypeElement().getValueAsString());

		assertEquals("out1", opDef.getParameter().get(2).getName());
		assertEquals("out", opDef.getParameter().get(2).getUse().toCode());
		assertEquals("1", opDef.getParameter().get(2).getMinElement().getValueAsString());
		assertEquals("2", opDef.getParameter().get(2).getMaxElement().getValueAsString());
		assertEquals("string", opDef.getParameter().get(2).getTypeElement().getValueAsString());
	}

	@Test
	public void testProviderWithRequiredAndOptional() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new ProviderWithRequiredAndOptional());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		ConformanceRestComponent rest = conformance.getRest().get(0);
		ConformanceRestResourceComponent res = rest.getResource().get(0);
		assertEquals("DiagnosticReport", res.getType());

		assertEquals(DiagnosticReport.SP_SUBJECT, res.getSearchParam().get(0).getName());
		assertEquals("identifier", res.getSearchParam().get(0).getChain().get(0).getValue());

		assertEquals(DiagnosticReport.SP_CODE, res.getSearchParam().get(1).getName());

		assertEquals(DiagnosticReport.SP_DATE, res.getSearchParam().get(2).getName());

		assertEquals(1, res.getSearchInclude().size());
		assertEquals("DiagnosticReport.result", res.getSearchInclude().get(0).getValue());
	}

	@Test
	public void testReadAndVReadSupported() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new VreadProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		conf = ourCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
		assertThat(conf, containsString("<interaction><code value=\"vread\"/></interaction>"));
		assertThat(conf, containsString("<interaction><code value=\"read\"/></interaction>"));
	}

	@Test
	public void testReadSupported() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new ReadProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		conf = ourCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
		assertThat(conf, not(containsString("<interaction><code value=\"vread\"/></interaction>")));
		assertThat(conf, containsString("<interaction><code value=\"read\"/></interaction>"));
	}

	@Test
	public void testSearchParameterDocumentation() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new SearchProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		boolean found = false;
		Collection<ResourceBinding> resourceBindings = rs.getResourceBindings();
		for (ResourceBinding resourceBinding : resourceBindings) {
			if (resourceBinding.getResourceName().equals("Patient")) {
				List<BaseMethodBinding<?>> methodBindings = resourceBinding.getMethodBindings();
				SearchMethodBinding binding = (SearchMethodBinding) methodBindings.get(0);
				for (IParameter next : binding.getParameters()) {
					SearchParameter param = (SearchParameter) next;
					if (param.getDescription().contains("The patient's identifier (MRN or other card number")) {
						found = true;
					}
				}
				found = true;
			}
		}
		assertTrue(found);
		Conformance conformance = sc.getServerConformance(createHttpServletRequest());

		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		assertThat(conf, containsString("<documentation value=\"The patient's identifier (MRN or other card number)\"/>"));
		assertThat(conf, containsString("<type value=\"token\"/>"));

	}

	/**
	 * See #286
	 */
	@Test
	public void testSearchReferenceParameterDocumentation() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new PatientResourceProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		boolean found = false;
		Collection<ResourceBinding> resourceBindings = rs.getResourceBindings();
		for (ResourceBinding resourceBinding : resourceBindings) {
			if (resourceBinding.getResourceName().equals("Patient")) {
				List<BaseMethodBinding<?>> methodBindings = resourceBinding.getMethodBindings();
				SearchMethodBinding binding = (SearchMethodBinding) methodBindings.get(0);
				SearchParameter param = (SearchParameter) binding.getParameters().get(25);
				assertEquals("The organization at which this person is a patient", param.getDescription());
				found = true;
			}
		}
		assertTrue(found);
		Conformance conformance = sc.getServerConformance(createHttpServletRequest());

		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

	}

	/**
	 * See #286
	 */
	@Test
	public void testSearchReferenceParameterWithWhitelistDocumentation() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new SearchProviderWithWhitelist());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		boolean found = false;
		Collection<ResourceBinding> resourceBindings = rs.getResourceBindings();
		for (ResourceBinding resourceBinding : resourceBindings) {
			if (resourceBinding.getResourceName().equals("Patient")) {
				List<BaseMethodBinding<?>> methodBindings = resourceBinding.getMethodBindings();
				SearchMethodBinding binding = (SearchMethodBinding) methodBindings.get(0);
				SearchParameter param = (SearchParameter) binding.getParameters().get(0);
				assertEquals("The organization at which this person is a patient", param.getDescription());
				found = true;
			}
		}
		assertTrue(found);
		Conformance conformance = sc.getServerConformance(createHttpServletRequest());

		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		ConformanceRestResourceComponent resource = findRestResource(conformance, "Patient");

		ConformanceRestResourceSearchParamComponent param = resource.getSearchParam().get(0);
		assertEquals("bar", param.getChain().get(0).getValue());
		assertEquals("foo", param.getChain().get(1).getValue());
		assertEquals(2, param.getChain().size());
	}

	@Test
	public void testSearchReferenceParameterWithList() throws Exception {

		RestfulServer rsNoType = new RestfulServer(ourCtx);
		rsNoType.registerProvider(new SearchProviderWithListNoType());
		ServerConformanceProvider scNoType = new ServerConformanceProvider(rsNoType);
		rsNoType.setServerConformanceProvider(scNoType);
		rsNoType.init(createServletConfig());
		scNoType.getServerConfiguration().setConformanceDate("2011-02-22T11:22:33Z");
		
		Conformance conformance = scNoType.getServerConformance(createHttpServletRequest());
		String confNoType = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(confNoType);

		RestfulServer rsWithType = new RestfulServer(ourCtx);
		rsWithType.registerProvider(new SearchProviderWithListWithType());
		ServerConformanceProvider scWithType = new ServerConformanceProvider(rsWithType);
		rsWithType.setServerConformanceProvider(scWithType);
		rsWithType.init(createServletConfig());
		scWithType.getServerConfiguration().setConformanceDate("2011-02-22T11:22:33Z");

		Conformance conformanceWithType = scWithType.getServerConformance(createHttpServletRequest());
		String confWithType = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformanceWithType);
		ourLog.info(confWithType);

		assertEquals(confNoType, confWithType);
	}

	@Test
	public void testSystemHistorySupported() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new SystemHistoryProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		conf = ourCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
		assertThat(conf, containsString("<interaction><code value=\"" + SystemRestfulInteraction.HISTORYSYSTEM.toCode() + "\"/></interaction>"));
	}

	@Test
	public void testTypeHistorySupported() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new TypeHistoryProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		conf = ourCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
		assertThat(conf, containsString("<interaction><code value=\"" + TypeRestfulInteraction.HISTORYTYPE.toCode() + "\"/></interaction>"));
	}

	@Test
	@Ignore
	public void testValidateGeneratedStatement() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new MultiOptionalProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest());
		ourLog.info(ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance));

		ValidationResult result = ourCtx.newValidator().validateWithResult(conformance);
		assertTrue(result.getMessages().toString(), result.isSuccessful());
	}

	private List<String> toOperationIdParts(List<ConformanceRestOperationComponent> theOperation) {
		ArrayList<String> retVal = Lists.newArrayList();
		for (ConformanceRestOperationComponent next : theOperation) {
			retVal.add(next.getDefinition().getReferenceElement().getIdPart());
		}
		return retVal;
	}

	private List<String> toOperationNames(List<ConformanceRestOperationComponent> theOperation) {
		ArrayList<String> retVal = Lists.newArrayList();
		for (ConformanceRestOperationComponent next : theOperation) {
			retVal.add(next.getName());
		}
		return retVal;
	}

	private Set<String> toStrings(List<CodeType> theType) {
		HashSet<String> retVal = new HashSet<String>();
		for (CodeType next : theType) {
			retVal.add(next.getValueAsString());
		}
		return retVal;
	}

	private void validate(OperationDefinition theOpDef) {
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(theOpDef);
		ourLog.info("Def: {}", conf);

		ValidationResult result = ourValidator.validateWithResult(theOpDef);
		String outcome = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(result.toOperationOutcome());
		ourLog.info("Outcome: {}", outcome);
		
		assertTrue(outcome, result.isSuccessful());
	}

	@AfterClass
	public static void afterClassClearContext() {
		TestUtil.clearAllStaticFieldsForUnitTest();
	}

	public static class ConditionalProvider implements IResourceProvider {

		@Create
		public MethodOutcome create(@ResourceParam Patient thePatient, @ConditionalUrlParam String theConditionalUrl) {
			return null;
		}

		@Delete
		public MethodOutcome delete(@IdParam IdType theId, @ConditionalUrlParam(supportsMultiple = true) String theConditionalUrl) {
			return null;
		}

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

		@Update
		public MethodOutcome update(@IdParam IdType theId, @ResourceParam Patient thePatient, @ConditionalUrlParam String theConditionalUrl) {
			return null;
		}

	}

	public static class InstanceHistoryProvider implements IResourceProvider {
		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

		@History
		public List<IBaseResource> history(@IdParam IdType theId) {
			return null;
		}

	}

	/**
	 * Created by dsotnikov on 2/25/2014.
	 */
	public static class MultiOptionalProvider {

		@Search(type = Patient.class)
		public Patient findPatient(@Description(shortDefinition = "The patient's identifier") @OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier,
				@Description(shortDefinition = "The patient's name") @OptionalParam(name = Patient.SP_NAME) StringParam theName) {
			return null;
		}

	}

	public static class MultiTypeEncounterProvider implements IResourceProvider {

		@Operation(name = "someOp")
		public ca.uhn.fhir.rest.server.IBundleProvider everything(javax.servlet.http.HttpServletRequest theServletRequest, @IdParam IdType theId,
				@OperationParam(name = "someOpParam1") DateType theStart, @OperationParam(name = "someOpParam2") Encounter theEnd) {
			return null;
		}

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Encounter.class;
		}

		@Validate
		public ca.uhn.fhir.rest.server.IBundleProvider validate(javax.servlet.http.HttpServletRequest theServletRequest, @IdParam IdType theId, @ResourceParam Encounter thePatient) {
			return null;
		}

	}

	public static class MultiTypePatientProvider implements IResourceProvider {

		@Operation(name = "someOp")
		public ca.uhn.fhir.rest.server.IBundleProvider everything(javax.servlet.http.HttpServletRequest theServletRequest, @IdParam IdType theId,
				@OperationParam(name = "someOpParam1") DateType theStart, @OperationParam(name = "someOpParam2") Patient theEnd) {
			return null;
		}

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

		@Validate
		public ca.uhn.fhir.rest.server.IBundleProvider validate(javax.servlet.http.HttpServletRequest theServletRequest, @IdParam IdType theId, @ResourceParam Patient thePatient) {
			return null;
		}

	}

	public static class NonConditionalProvider implements IResourceProvider {

		@Create
		public MethodOutcome create(@ResourceParam Patient thePatient) {
			return null;
		}

		@Delete
		public MethodOutcome delete(@IdParam IdType theId) {
			return null;
		}

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

		@Update
		public MethodOutcome update(@IdParam IdType theId, @ResourceParam Patient thePatient) {
			return null;
		}

	}

	public static class PlainProviderWithExtendedOperationOnNoType {

		@Operation(name = "plain", idempotent = true, returnParameters = { @OperationParam(min = 1, max = 2, name = "out1", type = StringType.class) })
		public ca.uhn.fhir.rest.server.IBundleProvider everything(javax.servlet.http.HttpServletRequest theServletRequest, @IdParam IdType theId, @OperationParam(name = "start") DateType theStart,
				@OperationParam(name = "end") DateType theEnd) {
			return null;
		}

	}

	public static class ProviderWithExtendedOperationReturningBundle implements IResourceProvider {

		@Operation(name = "everything", idempotent = true)
		public ca.uhn.fhir.rest.server.IBundleProvider everything(javax.servlet.http.HttpServletRequest theServletRequest, @IdParam IdType theId, @OperationParam(name = "start") DateType theStart,
				@OperationParam(name = "end") DateType theEnd) {
			return null;
		}

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

	}

	public static class ProviderWithRequiredAndOptional {

		@Description(shortDefinition = "This is a search for stuff!")
		@Search
		public List<DiagnosticReport> findDiagnosticReportsByPatient(@RequiredParam(name = DiagnosticReport.SP_SUBJECT + '.' + Patient.SP_IDENTIFIER) TokenParam thePatientId,
				@OptionalParam(name = DiagnosticReport.SP_CODE) TokenOrListParam theNames, @OptionalParam(name = DiagnosticReport.SP_DATE) DateRangeParam theDateRange,
				@IncludeParam(allow = { "DiagnosticReport.result" }) Set<Include> theIncludes) throws Exception {
			return null;
		}

	}

	public static class ReadProvider {

		@Search(type = Patient.class)
		public Patient findPatient(@Description(shortDefinition = "The patient's identifier (MRN or other card number)") @RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier) {
			return null;
		}

		@Read(version = false)
		public Patient readPatient(@IdParam IdType theId) {
			return null;
		}

	}

	public static class SearchProvider {

		@Search(type = Patient.class)
		public Patient findPatient1(@Description(shortDefinition = "The patient's identifier (MRN or other card number)") @RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier) {
			return null;
		}

		@Search(type = Patient.class)
		public Patient findPatient2(
				@Description(shortDefinition = "All patients linked to the given patient") @OptionalParam(name = "link", targetTypes = { Patient.class }) ReferenceAndListParam theLink) {
			return null;
		}

	}

	public static class SearchProviderWithWhitelist {

		@Search(type = Patient.class)
		public Patient findPatient1(@Description(shortDefinition = "The organization at which this person is a patient") @RequiredParam(name = Patient.SP_ORGANIZATION, chainWhitelist = { "foo",
				"bar" }) ReferenceAndListParam theIdentifier) {
			return null;
		}

	}

	public static class SearchProviderWithListNoType  implements IResourceProvider {

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}



		@Search()
		public List<Patient> findPatient1(@Description(shortDefinition = "The organization at which this person is a patient") @RequiredParam(name = Patient.SP_ORGANIZATION) ReferenceAndListParam theIdentifier) {
			return null;
		}

	}

	public static class SearchProviderWithListWithType  implements IResourceProvider {

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}



		@Search(type=Patient.class)
		public List<Patient> findPatient1(@Description(shortDefinition = "The organization at which this person is a patient") @RequiredParam(name = Patient.SP_ORGANIZATION) ReferenceAndListParam theIdentifier) {
			return null;
		}

	}

	
	public static class SystemHistoryProvider {

		@History
		public List<IBaseResource> history() {
			return null;
		}

	}

	public static class TypeHistoryProvider implements IResourceProvider {

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

		@History
		public List<IBaseResource> history() {
			return null;
		}

	}

	/**
	 * Created by dsotnikov on 2/25/2014.
	 */
	public static class VreadProvider {

		@Search(type = Patient.class)
		public Patient findPatient(@Description(shortDefinition = "The patient's identifier (MRN or other card number)") @RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier) {
			return null;
		}

		@Read(version = true)
		public Patient readPatient(@IdParam IdType theId) {
			return null;
		}

	}

}
