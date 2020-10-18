package com.sdc.test;

import com.sdc.test.parserhelper;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

//import org.hl7.fhir.r4.model.Observation;
import org.w3c.dom.Document;
//import org.w3c.dom.NodeList;

import ca.uhn.fhir.context.FhirContext;

//import org.w3c.dom.Node;
//import org.w3c.dom.Element;
import java.io.File;
//import java.util.ArrayList;

public class testparser {
	
	FhirContext ctx;
	
	public testparser() {
		this.ctx = FhirContext.forR4();
	}
	
    public static void main(String argv[]) {
  
      try {
  
      File xmlFile = new File("samplexml.xml");
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(xmlFile);
      
      parserhelper helper = new parserhelper();
  
      //optional, but recommended
      //read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
      doc.getDocumentElement().normalize();
  
      String statement = helper.parse(doc);
      System.out.println("Statement: " + statement);
      } catch (Exception e) {
      e.printStackTrace();
      }
    }
}