package de.dfki.mlt.transins.example3;

/*===========================================================================
  Copyright (C) 2009 by the Okapi Framework contributors
-----------------------------------------------------------------------------
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
===========================================================================*/

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.exceptions.OkapiException;
import net.sf.okapi.common.pipeline.BasePipelineStep;
import net.sf.okapi.common.resource.RawDocument;

@SuppressWarnings("all")
public class XsltTransformStep extends BasePipelineStep {

  private InputStream xstlInputstream;
  private boolean done;


  public XsltTransformStep(InputStream xstlInputstream) {

    done = false;
    this.xstlInputstream = xstlInputstream;
  }


  public String getName() {

    return "XSLT Processing Step";
  }


  public String getDescription() {

    return "Applies an XSLT template to the document.";
  }


  /**
   * handler takes a
   */
  @Override
  protected Event handleRawDocument(Event event) {

    ByteArrayOutputStream tempStream = new ByteArrayOutputStream();

    // get the input xml and xslt streams
    Reader XmlInput = ((RawDocument)event.getResource()).getReader();

    Source xmlSource = new StreamSource(XmlInput);
    Source xsltSource = new StreamSource(xstlInputstream);

    Result result = new StreamResult(tempStream);

    TransformerFactory transFact = TransformerFactory.newInstance();
    Transformer trans;
    try {
      trans = transFact.newTransformer(xsltSource);
      // do the transformation
      trans.transform(xmlSource, result);
    } catch (TransformerException e) {
      throw new OkapiException(e);
    }

    ByteArrayInputStream transformedInput = new ByteArrayInputStream(tempStream.toByteArray());

    // overwrite our event to the new transformed content
    event.setResource(new RawDocument(transformedInput, "UTF-8", new LocaleId("en")));

    // This step is done generating events
    done = true;

    return event;
  }


  @Override
  public boolean isDone() {

    return done;
  }
}
