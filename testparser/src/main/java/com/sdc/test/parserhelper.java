package com.sdc.test;


import org.hl7.fhir.r4.model.BooleanType;
//import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
//import org.xml.sax.InputSource;
//import org.xml.sax.SAXException;

import ca.uhn.fhir.context.FhirContext;
//import ca.uhn.fhir.rest.api.MethodOutcome;
//import ca.uhn.fhir.rest.client.api.IGenericClient;

import org.w3c.dom.Node;
import org.w3c.dom.Element;

//import java.io.IOException;
//import java.io.StringReader;
import java.util.ArrayList;

//import javax.xml.parsers.DocumentBuilder;
//import javax.xml.parsers.DocumentBuilderFactory;
//import javax.xml.parsers.ParserConfigurationException;

public class parserhelper {

	FhirContext ctx;
	
	public parserhelper() {
		this.ctx = FhirContext.forR4();
	}
	
	public String parse (Document document) {
		StringBuilder stringbuilder = new StringBuilder();
		ArrayList<Observation> observations = parseSDCForm(document, ctx);
		int obsListSize = observations.size();
		for (int i = 0; i < obsListSize; i++) {
			Observation obs = observations.get(i);
			String encoded = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(observations.get(i));
			stringbuilder.append(encoded + "\n************************************************************\n");
		}
		return stringbuilder.toString();
	}
    
//    public static void parseSDCForm(Document document) {
    public static ArrayList<Observation> parseSDCForm(Document document, FhirContext ctx) {

		// get forminstanceVersion and ID
		String Id = getFormID(document);
		System.out.println("Form ID: " + Id);
		// get Body node
		Node body = getBodyElement(document);
		// get all the children of the body node
		ArrayList<Node> childrenOfBody = getAllChildrenFromBody(body);
		System.out.println("# of children in Body: " + childrenOfBody.size());
		// there should be only 1 child in the body - "ChildItems"
		Element childItems = (Element) childrenOfBody.get(0);
		NodeList questionList = getAllQuestionNodes(childItems);
		System.out.println("# of questions: " + questionList.getLength());
		// get the list of questions with selected = "true";
		ArrayList<Observation> answeredQuestions = getSelectedTrueQuestions(questionList, Id, ctx);
		return answeredQuestions;
	}
    
    public void printctx() {
    	System.out.println(ctx);
    }
    
    /**
	 * This will traverse through the list of selected questions
	 * 
	 * @param questionList
	 * @param Id
	 * @param ctx
	 * @return
	 */
	public static ArrayList<Observation> getSelectedTrueQuestions(NodeList questionList, String Id, FhirContext ctx) {

		ArrayList<Observation> observations = new ArrayList<Observation>();
		Observation observation = null;
		for (int i = 0; i < questionList.getLength(); i++) {
			Element questionElement = (Element) questionList.item(i);
			String questionID = questionElement.getAttribute("ID");
			// get the listFieldElement
			boolean isListQuestion = isQuestionAListQuestion(questionElement);
			boolean isTextQuestion = isQuestionATextQuestion(questionElement);
			if (isListQuestion) {
				boolean isMultiSelect = getListFieldEelementToCheckForMultiSelect(questionElement);
				// get the ListItems under this question where selected = true
				NodeList listItemList = questionElement.getElementsByTagName("ListItem");
				if (!isMultiSelect) {
					for (int j = 0; j < listItemList.getLength(); j++) {
						Element listItemElement = (Element) listItemList.item(j);
						if (listItemElement.hasAttribute("selected")) {
							Element parentQuestion = (Element) listItemElement.getParentNode().getParentNode()
									.getParentNode();
							if (parentQuestion.getAttribute("ID").equals(questionElement.getAttribute("ID"))) {
								System.out.println("QUESTION.ID: " + questionElement.getAttribute("ID"));
								System.out.println("LISTITEM.ID: " + listItemElement.getAttribute("ID"));
								System.out.println("LISTITEM.TITLE: " + listItemElement.getAttribute("title"));
								System.out.println("*******************************************************************");
								observation = buildObservationResource(questionElement, listItemElement, Id, ctx);
								observations.add(observation);
							}

						}
					}
				} else {

					ArrayList<Element> listElementsAnswered = new ArrayList<Element>();
					// check if there are any selected answers before hand!
					for (int j = 0; j < listItemList.getLength(); j++) {
						Element listItemElement = (Element) listItemList.item(j);
						if (listItemElement.hasAttribute("selected")) {
							Element parentQuestion = (Element) listItemElement.getParentNode().getParentNode()
									.getParentNode();
							if (parentQuestion.getAttribute("ID").equals(questionID)) {
								listElementsAnswered.add(listItemElement);
							}
						}
					}

					// Now if there are selected answers then only add them as components
					if (!listElementsAnswered.isEmpty()) {
						observation = buildMultiSelectObservationResource(questionElement, Id, ctx);
						observations.add(observation);
						addComponentToObservation(observation, listElementsAnswered);
						String encoded = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(observation);
						System.out.println(encoded);
						System.out.println("*******************************************************************");
					}
				}
			} else if (isTextQuestion){
				Element textQuestion = getTextQuestion(questionElement);
				Element textResponse = getTextQuestionResponse(textQuestion);
				if (isTextQuestionIntegerAndHasResponse(textResponse)) {
					String response = getIntegerResponse(textResponse);
					System.out.println("Question is integer");
					System.out.println("response: " + response);
					observation = buildIntegerObservationResource(questionElement, response, Id, ctx);
					observations.add(observation);
				} else if (isTextQuestionDecimalAndHasResponse(textResponse)) {
					System.out.println("Question is decimal");
				}
				else if (isTextQuestionStringAndHasResponse(textResponse)) {
					String response = getStringResponse(textResponse);
					System.out.println("Question is String");
					System.out.println("response: " + response);
					observation = buildStringObservationResource(questionElement, response, Id, ctx);
					observations.add(observation);
				}
				else if (isTextQuestionBooleanAndHasResponse(textResponse)) {
					System.out.println("Question is boolean");
				}
				else if (isTextQuestionDateAndHasResponse(textResponse)) {
					System.out.println("Question is date");
				}
				else if (isTextQuestionDateTimeAndHasResponse(textResponse)) {
					System.out.println("Question is dateTime");
				} else {
					System.out.println("ERROR. TextQuestion type is not accounted for!!!!!");
				}
			} else {
				System.out.println("Question NOT List or Text");
				System.out.println("QUESTION.ID: " + questionElement.getAttribute("ID"));
			}
		}
		return observations;
	}
	
	public static boolean isQuestionAListQuestion(Element questionElement) {
		NodeList listFieldElementList = questionElement.getElementsByTagName("ListField");
		if (listFieldElementList.getLength() > 0) {
			return true;
		}
		return false;
	}
    
	public static boolean isQuestionATextQuestion(Element questionElement) {
		NodeList responseFieldElementList = questionElement.getElementsByTagName("ResponseField");
		if (responseFieldElementList.getLength() > 0) {
			return true;
		}
		return false;
	}
	
	public static Element getTextQuestion(Element questionElement) {
		Element textQuestionElement = (Element) questionElement.getElementsByTagName("ResponseField").item(0);
		return textQuestionElement;
	}
	
	public static Element getTextQuestionResponse(Element textQuestion) {
		Element responseElement = (Element) textQuestion.getElementsByTagName("Response").item(0);
		return responseElement;
	}
	
	public static boolean isTextQuestionIntegerAndHasResponse(Element textQuestionResponse) {
		NodeList integerElementList = textQuestionResponse.getElementsByTagName("integer");
		if (integerElementList.getLength() > 0) {
			Element integerElement = (Element) integerElementList.item(0);
			if (integerElement.hasAttribute("val")) {
				return true;
			}
		}
		return false;
	}
	
	public static String getIntegerResponse (Element textQuestionResponse) {
		Element integerElement = (Element) textQuestionResponse.getElementsByTagName("integer").item(0);
		return integerElement.getAttribute("val");
	}
	
	public static boolean isTextQuestionDecimalAndHasResponse(Element textQuestionResponse) {
		NodeList decimalElementList = textQuestionResponse.getElementsByTagName("decimal");
		if (decimalElementList.getLength() > 0) {
			Element decimalElement = (Element) decimalElementList.item(0);
			if (decimalElement.hasAttribute("val")) {
				return true;
			}
		}
		return false;
	}
	
	public static String getDecimalResponse (Element textQuestionResponse) {
		Element decimalElement = (Element) textQuestionResponse.getElementsByTagName("decimal").item(0);
		return decimalElement.getAttribute("val");
	}
	
	public static boolean isTextQuestionStringAndHasResponse(Element textQuestionResponse) {
		NodeList stringElementList = textQuestionResponse.getElementsByTagName("string");
		if (stringElementList.getLength() > 0) {
			Element stringElement = (Element) stringElementList.item(0);
			if (stringElement.hasAttribute("val")) {
				return true;
			}
		}
		return false;
	}
	
	public static String getStringResponse (Element textQuestionResponse) {
		Element stringElement = (Element) textQuestionResponse.getElementsByTagName("string").item(0);
		return stringElement.getAttribute("val");
	}
	
	public static boolean isTextQuestionBooleanAndHasResponse(Element textQuestionResponse) {
		NodeList booleanElementList = textQuestionResponse.getElementsByTagName("boolean");
		if (booleanElementList.getLength() > 0) {
			Element booleanElement = (Element) booleanElementList.item(0);
			if (booleanElement.hasAttribute("val")) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean isTextQuestionDateAndHasResponse(Element textQuestionResponse) {
		NodeList dateElementList = textQuestionResponse.getElementsByTagName("date");
		if (dateElementList.getLength() > 0) {
			Element dateElement = (Element) dateElementList.item(0);
			if (dateElement.hasAttribute("val")) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean isTextQuestionDateTimeAndHasResponse(Element textQuestionResponse) {
		NodeList dateTimeElementList = textQuestionResponse.getElementsByTagName("dateTime");
		if (dateTimeElementList.getLength() > 0) {
			Element dateTimeElement = (Element) dateTimeElementList.item(0);
			if (dateTimeElement.hasAttribute("val")) {
				return true;
			}
		}
		return false;
	}
	
    /**
	 * This will traverse through the list of selected questions
	 * 
	 * @param questionList
	 * @param Id
	 * @param ctx
	 * @return
	 */
	public static ArrayList<Observation> getSelectedTrueQuestionsOld(NodeList questionList, String Id, FhirContext ctx) {

		ArrayList<Observation> observations = new ArrayList<Observation>();
		Observation observation = null;
		for (int i = 0; i < questionList.getLength(); i++) {
			Element questionElement = (Element) questionList.item(i);
			// get the listFieldElement
			boolean isMultiSelect = getListFieldEelementToCheckForMultiSelect(questionElement);
			// get the ListItems under this question where selected = true
			NodeList listItemList = questionElement.getElementsByTagName("ListItem");
			if (!isMultiSelect) {
				for (int j = 0; j < listItemList.getLength(); j++) {
					Element listItemElement = (Element) listItemList.item(j);
					if (listItemElement.hasAttribute("selected")) {
						Element parentQuestion = (Element) listItemElement.getParentNode().getParentNode()
								.getParentNode();
						if (parentQuestion.getAttribute("ID").equals(questionElement.getAttribute("ID"))) {
							System.out.println("QUESTION.ID: " + questionElement.getAttribute("ID"));
							System.out.println("LISTITEM.ID: " + listItemElement.getAttribute("ID"));
							System.out.println("LISTITEM.TITLE: " + listItemElement.getAttribute("title"));
							System.out.println("*******************************************************************");
							observation = buildObservationResource(questionElement, listItemElement, Id, ctx);
							observations.add(observation);
						}

					}
				}
			} else {

				String questionID = questionElement.getAttribute("ID");
				ArrayList<Element> listElementsAnswered = new ArrayList<Element>();
				// check if there are any selected answers before hand!
				for (int j = 0; j < listItemList.getLength(); j++) {
					Element listItemElement = (Element) listItemList.item(j);
					if (listItemElement.hasAttribute("selected")) {
						Element parentQuestion = (Element) listItemElement.getParentNode().getParentNode()
								.getParentNode();
						if (parentQuestion.getAttribute("ID").equals(questionID)) {
							listElementsAnswered.add(listItemElement);
						}
					}
				}

				// Now if there are selected answers then only add them as components
				if (!listElementsAnswered.isEmpty()) {
					observation = buildMultiSelectObservationResource(questionElement, Id, ctx);
					observations.add(observation);
					addComponentToObservation(observation, listElementsAnswered);
					String encoded = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(observation);
					System.out.println(encoded);
					System.out.println("*******************************************************************");
				}
			}
		}
		return observations;
	}
	
	public static boolean getListFieldEelementToCheckForMultiSelect(Element questionElement) {
		NodeList listFieldElementList = questionElement.getElementsByTagName("ListField");
		if (listFieldElementList.getLength() > 0) {
			Element listFieldElement = (Element) listFieldElementList.item(0);
			if (listFieldElement.hasAttribute("maxSelections")) {
				return true;
			}
		}
		return false;
	}
	
	public static Observation buildIntegerObservationResource(Element questionElement, String integerResponse, String id,
			FhirContext ctx) {

		Observation observation = new Observation();
		observation.setSubject(new Reference("Patient/6754"));
		observation.addPerformer().setReference("Practitioner/pathpract1");
		observation.addIdentifier().setSystem("https://CAP.org")
				.setValue(id + "#" + questionElement.getAttribute("ID"));
		observation.setStatus(ObservationStatus.FINAL);
		observation.getCode().addCoding().setSystem("https://CAP.org").setCode(questionElement.getAttribute("ID"))
				.setDisplay(questionElement.getAttribute("title"));
		observation.setValue(new IntegerType(integerResponse)).getValueIntegerType();
		observation.addDerivedFrom().setReference("DocumentReference/" + id);
		String encoded = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(observation);
		System.out.println(encoded);
		System.out.println("*******************************************************************");
		return observation;
	}
	
//	public static Observation buildDecimalObservationResource(Element questionElement, String decimalResponse, String id,
//			FhirContext ctx) {
//
//		Observation observation = new Observation();
//		observation.setSubject(new Reference("Patient/6754"));
//		observation.addPerformer().setReference("Practitioner/pathpract1");
//		observation.addIdentifier().setSystem("https://CAP.org")
//				.setValue(id + "#" + questionElement.getAttribute("ID"));
//		observation.setStatus(ObservationStatus.FINAL);
//		observation.getCode().addCoding().setSystem("https://CAP.org").setCode(questionElement.getAttribute("ID"))
//				.setDisplay(questionElement.getAttribute("title"));
//		observation.setValue(new DecimalType(decimalResponse)).getValueDecimalType();
//		observation.addDerivedFrom().setReference("DocumentReference/" + id);
//		String encoded = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(observation);
//		System.out.println(encoded);
//		System.out.println("*******************************************************************");
//		return observation;
//	}
	
	public static Observation buildStringObservationResource(Element questionElement, String stringResponse, String id,
			FhirContext ctx) {

		Observation observation = new Observation();
		observation.setSubject(new Reference("Patient/6754"));
		observation.addPerformer().setReference("Practitioner/pathpract1");
		observation.addIdentifier().setSystem("https://CAP.org")
				.setValue(id + "#" + questionElement.getAttribute("ID"));
		observation.setStatus(ObservationStatus.FINAL);
		observation.getCode().addCoding().setSystem("https://CAP.org").setCode(questionElement.getAttribute("ID"))
				.setDisplay(questionElement.getAttribute("title"));
		observation.setValue(new StringType(stringResponse)).getValueStringType();
		observation.addDerivedFrom().setReference("DocumentReference/" + id);
		String encoded = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(observation);
		System.out.println(encoded);
		System.out.println("*******************************************************************");
		return observation;
	}
	
	public static Observation buildBooleanObservationResource(Element questionElement, String booleanResponse, String id,
			FhirContext ctx) {

		Observation observation = new Observation();
		observation.setSubject(new Reference("Patient/6754"));
		observation.addPerformer().setReference("Practitioner/pathpract1");
		observation.addIdentifier().setSystem("https://CAP.org")
				.setValue(id + "#" + questionElement.getAttribute("ID"));
		observation.setStatus(ObservationStatus.FINAL);
		observation.getCode().addCoding().setSystem("https://CAP.org").setCode(questionElement.getAttribute("ID"))
				.setDisplay(questionElement.getAttribute("title"));
		observation.setValue(new BooleanType(booleanResponse)).getValueBooleanType();
		observation.addDerivedFrom().setReference("DocumentReference/" + id);
		String encoded = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(observation);
		System.out.println(encoded);
		System.out.println("*******************************************************************");
		return observation;
	}
	
//	public static Observation buildDateObservationResource(Element questionElement, String dateResponse, String id,
//			FhirContext ctx) {
//
//		Observation observation = new Observation();
//		observation.setSubject(new Reference("Patient/6754"));
//		observation.addPerformer().setReference("Practitioner/pathpract1");
//		observation.addIdentifier().setSystem("https://CAP.org")
//				.setValue(id + "#" + questionElement.getAttribute("ID"));
//		observation.setStatus(ObservationStatus.FINAL);
//		observation.getCode().addCoding().setSystem("https://CAP.org").setCode(questionElement.getAttribute("ID"))
//				.setDisplay(questionElement.getAttribute("title"));
//		observation.setValue(new DateType(dateResponse)).getValueDateType();
//		observation.addDerivedFrom().setReference("DocumentReference/" + id);
//		String encoded = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(observation);
//		System.out.println(encoded);
//		System.out.println("*******************************************************************");
//		return observation;
//	}
	
	public static Observation buildDateTimeObservationResource(Element questionElement, String dateTimeResponse, String id,
			FhirContext ctx) {

		Observation observation = new Observation();
		observation.setSubject(new Reference("Patient/6754"));
		observation.addPerformer().setReference("Practitioner/pathpract1");
		observation.addIdentifier().setSystem("https://CAP.org")
				.setValue(id + "#" + questionElement.getAttribute("ID"));
		observation.setStatus(ObservationStatus.FINAL);
		observation.getCode().addCoding().setSystem("https://CAP.org").setCode(questionElement.getAttribute("ID"))
				.setDisplay(questionElement.getAttribute("title"));
		observation.setValue(new DateTimeType(dateTimeResponse)).getValueDateTimeType();
		observation.addDerivedFrom().setReference("DocumentReference/" + id);
		String encoded = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(observation);
		System.out.println(encoded);
		System.out.println("*******************************************************************");
		return observation;
	}

	public static Observation buildObservationResource(Element questionElement, Element listItemElement, String id,
			FhirContext ctx) {

		Observation observation = new Observation();
		observation.setSubject(new Reference("Patient/6754"));
		observation.addPerformer().setReference("Practitioner/pathpract1");
		observation.addIdentifier().setSystem("https://CAP.org")
				.setValue(id + "#" + questionElement.getAttribute("ID"));
		observation.setStatus(ObservationStatus.FINAL);
		observation.getCode().addCoding().setSystem("https://CAP.org").setCode(questionElement.getAttribute("ID"))
				.setDisplay(questionElement.getAttribute("title"));
		observation.setValue(new CodeableConcept()).getValueCodeableConcept().addCoding().setSystem("https://CAP.org")
				.setCode(listItemElement.getAttribute("ID")).setDisplay(listItemElement.getAttribute("title"));
		observation.addDerivedFrom().setReference("DocumentReference/" + id);
		String encoded = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(observation);
		System.out.println(encoded);
		System.out.println("*******************************************************************");
		return observation;

	}

	public static Observation buildMultiSelectObservationResource(Element questionElement, String id, FhirContext ctx) {
		Observation observation = new Observation();
		observation.addIdentifier().setSystem("https://CAP.org")
				.setValue(id + "." + questionElement.getAttribute("ID"));
		observation.setStatus(ObservationStatus.FINAL);
		observation.getCode().addCoding().setSystem("https://CAP.org").setCode(questionElement.getAttribute("ID"))
				.setDisplay(questionElement.getAttribute("title"));
		observation.addDerivedFrom().setReference("DocumentReference/" + id);
		return observation;
	}

	public static Observation addComponentToObservation(Observation observation,
			ArrayList<Element> listElementsAnswered) {

		for (Element element : listElementsAnswered) {

			observation.addComponent().getCode().addCoding().setSystem("https://CAP.org")
					.setCode(element.getAttribute("ID")).setDisplay(element.getAttribute("title"));
		}

		return observation;
	}

	/**
	 * Method that gets the root Element - SDCPackage
	 * 
	 * @param document
	 * @return
	 */
	public static Element getRootElement(Document document) {
		Element root = document.getDocumentElement(); // SDCPackage
		System.out.println("Root: " + root.getNodeName());
		return root;
	}

	/**
	 * Method that return the Body Element
	 * 
	 * @param document
	 * @return
	 */
	public static Node getBodyElement(Document document) {
		Node body = null;
		NodeList nList = document.getElementsByTagName("Body");
		body = nList.item(0);
		return body;
	}
	
	/**
	 * Method that gets the Children of the Body element. There should be only 1
	 * ChildItems
	 * 
	 * @param body
	 * @return
	 */
	public static ArrayList<Node> getAllChildrenFromBody(Node body) {
		NodeList children = body.getChildNodes();
		return removeWhiteSpaces(children);
	}

	/**
	 * Method that gets all the sections form the ChildItems under Body
	 * 
	 * @param childItems
	 * @return
	 */
	public static NodeList getSectionsFromChildItems(Element childItems) {
		NodeList nodeList = childItems.getElementsByTagName("Section");
		return nodeList;
	}
	
	/**
	 * Method that removes all the "# text" elements form the list
	 * 
	 * @param nodeList
	 * @return
	 */
	public static ArrayList<Node> removeWhiteSpaces(NodeList nodeList) {
		ArrayList<Node> returnList = new ArrayList<Node>();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			if (Node.ELEMENT_NODE == node.getNodeType()) {
				returnList.add(node);
			}
		}

		return returnList;
	}
	
	public static NodeList getAllQuestionNodes(Element childItems) {
		NodeList questionList = childItems.getElementsByTagName("Question");
		return questionList;
	}

	public static void printQuestionName(ArrayList<Node> questionList) {
		for (int i = 0; i < questionList.size(); i++) {
		}
	}
	
	/**
	 * Method that get the formInstranceVrsionURI and ID
	 * 
	 * @param document
	 * @return
	 */
	public static String getFormID(Document document) {
		Element root = getRootElement(document);
		NodeList nodeList = root.getElementsByTagName("FormDesign");
		Element formDesignNode = (Element) nodeList.item(0);
		return formDesignNode.getAttribute("ID") + formDesignNode.getAttribute("formInstanceVersionURI");
	}
  
  }