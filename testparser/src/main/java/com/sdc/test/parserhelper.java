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
import org.hl7.fhir.r4.model.Quantity;
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
	final static String INTEGER = "integer";
	final static String DECIMAL = "decimal";
	final static String STRING = "string";
	final static String BOOLEAN = "boolean";
	final static String DATE = "date";
	final static String DATETIME = "dateTime";
	
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
				Element textQuestionResponse = getTextQuestionResponse(textQuestion);
				if (isTextQuestionOfTypeAndHasResponse(INTEGER, textQuestionResponse)) {
					buildAndAddObservationForType(INTEGER, textQuestionResponse, questionElement, Id, ctx, observations);
				} else if (isTextQuestionOfTypeAndHasResponse(DECIMAL, textQuestionResponse)) {
					buildAndAddObservationForType(DECIMAL, textQuestionResponse, questionElement, Id, ctx, observations);
				}
				else if (isTextQuestionOfTypeAndHasResponse(STRING, textQuestionResponse)) {
					buildAndAddObservationForType(STRING, textQuestionResponse, questionElement, Id, ctx, observations);
				}
				else if (isTextQuestionOfTypeAndHasResponse(BOOLEAN, textQuestionResponse)) {
					buildAndAddObservationForType(BOOLEAN, textQuestionResponse, questionElement, Id, ctx, observations);
				}
				else if (isTextQuestionOfTypeAndHasResponse(DATE, textQuestionResponse)) {
					System.out.println("Question is date");
				}
				else if (isTextQuestionOfTypeAndHasResponse(DATETIME, textQuestionResponse)) {
					buildAndAddObservationForType(DATETIME, textQuestionResponse, questionElement, Id, ctx, observations);
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
	
	public static void buildAndAddObservationForType (String type, Element textQuestionResponse, Element questionElement, 
			String Id, FhirContext ctx, ArrayList<Observation> observations) {
		String response = getTextResponseForType(type, textQuestionResponse);
		Observation observation = buildTextObservationResource(type, questionElement, response, Id, ctx);
		observations.add(observation);
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
	
	public static String getTextResponseForType (String type, Element textQuestionResponse) {
		Element integerElement = (Element) textQuestionResponse.getElementsByTagName(type).item(0);
		return integerElement.getAttribute("val");
	}
	
	public static boolean isTextQuestionOfTypeAndHasResponse(String type, Element textQuestionResponse) {
		NodeList dateTimeElementList = textQuestionResponse.getElementsByTagName(type);
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
	
	public static Observation buildTextObservationResource(String type, Element questionElement, String textResponse, String id,
			FhirContext ctx) {

		Observation observation = new Observation();
		observation.setSubject(new Reference("Patient/6754"));
		observation.addPerformer().setReference("Practitioner/pathpract1");
		observation.addIdentifier().setSystem("https://CAP.org")
				.setValue(id + "#" + questionElement.getAttribute("ID"));
		observation.setStatus(ObservationStatus.FINAL);
		observation.getCode().addCoding().setSystem("https://CAP.org").setCode(questionElement.getAttribute("ID"))
				.setDisplay(questionElement.getAttribute("title"));
		if (type == INTEGER) {
			observation.setValue(new IntegerType(textResponse)).getValueIntegerType();
		} else if (type == DECIMAL) {
			observation.setValue(new Quantity(Double.parseDouble(textResponse))).getValueQuantity();
		} else if (type == STRING) {
			observation.setValue(new StringType(textResponse)).getValueStringType();
		} else if (type == BOOLEAN) {
			observation.setValue(new BooleanType(textResponse)).getValueBooleanType();
		} else if (type == DATETIME) {
			observation.setValue(new DateTimeType(textResponse)).getValueDateTimeType();
		} else {
			System.out.println("ERROR: BUILDING OBERVATION FOR UNSUPPORTED TYPE");
		}
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