/*
 *
 * Copyright (C) 2007-2008 sibyl project
 * http://code.google.com/p/sibyl/
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

package com.sibyl;


import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.util.Log;

/**
 * Parse amazon xml answer when searching for cover
 *
 * @author sibyl
 *
 */
public class AmazonParser extends DefaultHandler{

    private StringBuffer buffer;
    private static final String TAG = "LargeImage";
    private static final String TAG_CONTENT = "URL";

    // true when we can read data
    private boolean read;
    // true when we have found the TAG
    private boolean found;
    // false when we have read first data
    private boolean first;

    public void startDocument() throws SAXException {
        // initial values
        first = true;
        read = false;
        found = false;
    }
   
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException{
        if(first && qName.equals(TAG)){
            found = true;
        }else if( first && found && qName.equals(TAG_CONTENT)){
            read = true;
        }
    }

    public void endElement(String uri, String localName, String qName) throws SAXException{
        if(found && qName.equals(TAG)){
            found = false;
            first = false;
        }else if( found && qName.equals(TAG_CONTENT)){
            read = false;
        }
    }

    public void characters(char[] ch,int start, int length) throws SAXException{
        if(read){
            buffer = new StringBuffer();
            buffer.append(ch, start, length);
            Log.d("AmazonParser.characters", buffer.toString());
            read = false;
        }
    }

    /**
     * get result (cover url)
     *
     * @return null if nothing found
     */
    public String getResult(){
        return buffer == null ? null : buffer.toString();
    }

}

