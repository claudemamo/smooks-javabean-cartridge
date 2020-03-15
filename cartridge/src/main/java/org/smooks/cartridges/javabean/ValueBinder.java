/*
	Milyn - Copyright (C) 2006 - 2010

	This library is free software; you can redistribute it and/or
	modify it under the terms of the GNU Lesser General Public
	License (version 2.1) as published by the Free Software
	Foundation.

	This library is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

	See the GNU Lesser General Public License for more details:
	http://www.gnu.org/licenses/lgpl.txt
*/
package org.smooks.cartridges.javabean;

import org.smooks.SmooksException;
import org.smooks.cdr.SmooksConfigurationException;
import org.smooks.cdr.annotation.AnnotationConstants;
import org.smooks.cdr.annotation.AppContext;
import org.smooks.cdr.annotation.ConfigParam;
import org.smooks.container.ApplicationContext;
import org.smooks.container.ExecutionContext;
import org.smooks.delivery.Fragment;
import org.smooks.delivery.annotation.Initialize;
import org.smooks.delivery.dom.DOMElementVisitor;
import org.smooks.delivery.ordering.Producer;
import org.smooks.delivery.sax.SAXElement;
import org.smooks.delivery.sax.SAXUtil;
import org.smooks.delivery.sax.SAXVisitAfter;
import org.smooks.delivery.sax.SAXVisitBefore;
import org.smooks.event.report.annotation.VisitAfterReport;
import org.smooks.event.report.annotation.VisitBeforeReport;
import org.smooks.javabean.DataDecodeException;
import org.smooks.javabean.DataDecoder;
import org.smooks.javabean.context.BeanContext;
import org.smooks.javabean.repository.BeanId;
import org.smooks.util.CollectionsUtil;
import org.smooks.xml.DomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Value Binder.
 * <p/>
 * This class can be used to configure a Smooks instance for creating value
 * objects using the Smooks DataDecoders.
 * <h3>XML Schema & Namespace</h3>
 * The Value Binder XML configuration schema is in the following XML Schema Namespace:
 * <p/>
 * <a href="https://www.smooks.org/xsd/smooks/javabean-1.6.xsd"><b>https://www.smooks.org/xsd/smooks/javabean-1.6.xsd</b></a>
 * <p/>
 * The value binder element is '&lt;value&gt;'. Take a look in the schema for all
 * the configuration attributes.
 *
 * <h3>Programmatic configuration</h3>
 * The value binder can be programmatic configured using the {@link Value} Object.
 *
 * <h3>Example</h3>
 * Taking the "classic" Order message as an example and getting the order number and
 * name as Value Objects in the form of an Integer and String.
 * <h4>The Message</h4>
 * <pre>
 * &lt;order xmlns="http://x"&gt;
 *     &lt;header&gt;
 *         &lt;y:date xmlns:y="http://y"&gt;Wed Nov 15 13:45:28 EST 2006&lt;/y:date&gt;
 *         &lt;customer number="123123"&gt;Joe&lt;/customer&gt;
 *         &lt;privatePerson&gt;&lt;/privatePerson&gt;
 *     &lt;/header&gt;
 *     &lt;order-items&gt;
 *         &lt;!-- .... --&gt;
 *     &lt;/order-items&gt;
 * &lt;/order&gt;
 * </pre>
 *
 * <h4>The Binding Configuration</h4>
 * <pre>
 * &lt;?xml version=&quot;1.0&quot;?&gt;
 * &lt;smooks-resource-list xmlns=&quot;https://www.smooks.org/xsd/smooks-1.2.xsd&quot; xmlns:jb=&quot;https://www.smooks.org/xsd/smooks/javabean-1.6.xsd&quot;&gt;
 *
 *    &lt;jb:value
 *       beanId=&quot;customerName&quot;
 *       data=&quot;customer&quot;
 *       default=&quot;unknown&quot;
 *    /&gt;
 *
 *    &lt;jb:value
 *       beanId=&quot;customerNumber&quot;
 *       data=&quot;customer/@number&quot;
 *	     decoder=&quot;Integer&quot;
 *    /&gt;
 *
 * &lt;/smooks-resource-list&gt;
 * </pre>
 *
 * @author <a href="mailto:maurice.zeijen@smies.com">maurice.zeijen@smies.com</a>
 */
@VisitBeforeReport(condition = "parameters.containsKey('valueAttributeName')",
        summary = "Creating object under bean id <b>${resource.parameters.beanId}</b> with a value from the attribute <b>${resource.parameters.valueAttributeName}</b>.",
        detailTemplate = "reporting/ValueBinderReport_Before.html")
@VisitAfterReport(condition = "!parameters.containsKey('valueAttributeName')",
        summary = "Creating object <b>${resource.parameters.beanId}</b> with a value from this element.",
        detailTemplate = "reporting/ValueBinderReport_After.html")
public class ValueBinder implements DOMElementVisitor, SAXVisitBefore, SAXVisitAfter, Producer {

	private static final Logger LOGGER = LoggerFactory.getLogger(ValueBinder.class);

    @ConfigParam(name="beanId")
    private String beanIdName;

    @ConfigParam(defaultVal = AnnotationConstants.NULL_STRING)
    private String valueAttributeName;

    @ConfigParam(name="default", defaultVal = AnnotationConstants.NULL_STRING)
    private String defaultValue;

    @ConfigParam(name="type", defaultVal = "String")
    private String typeAlias;

    private BeanId beanId;

    @AppContext
    private ApplicationContext appContext;

    private boolean isAttribute;

    private DataDecoder decoder;

    /**
     *
     */
    public ValueBinder() {
	}

    /**
     * @param beanId
     */
	public ValueBinder(String beanId) {
		this.beanIdName = beanId;
	}

	/**
	 * @return the beanIdName
	 */
	public String getBeanIdName() {
		return beanIdName;
	}

	/**
	 * @param beanIdName the beanIdName to set
	 */
	public void setBeanIdName(String beanIdName) {
		this.beanIdName = beanIdName;
	}

	/**
	 * @return the valueAttributeName
	 */
	public String getValueAttributeName() {
		return valueAttributeName;
	}

	/**
	 * @param valueAttributeName the valueAttributeName to set
	 */
	public void setValueAttributeName(String valueAttributeName) {
		this.valueAttributeName = valueAttributeName;
	}

	/**
	 * @return the defaultValue
	 */
	public String getDefaultValue() {
		return defaultValue;
	}

	/**
	 * @param defaultValue the defaultValue to set
	 */
	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	/**
	 * @return the typeAlias
	 */
	public String getTypeAlias() {
		return typeAlias;
	}

	/**
	 * @param typeAlias the typeAlias to set
	 */
	public void setTypeAlias(String typeAlias) {
		this.typeAlias = typeAlias;
	}

	/**
	 * @return the decoder
	 */
	public DataDecoder getDecoder() {
		return decoder;
	}

	/**
	 * @param decoder the decoder to set
	 */
	public void setDecoder(DataDecoder decoder) {
		this.decoder = decoder;
	}

	/**
     * Set the resource configuration on the bean populator.
     * @throws SmooksConfigurationException Incorrectly configured resource.
     */
    @Initialize
    public void initialize() throws SmooksConfigurationException {
    	isAttribute = (valueAttributeName != null);

        beanId = appContext.getBeanIdStore().register(beanIdName);

        if(LOGGER.isDebugEnabled()) {
        	LOGGER.debug("Value Binder created for [" + beanIdName + "].");
        }
    }

	public void visitBefore(Element element, ExecutionContext executionContext)
			throws SmooksException {
		if(isAttribute) {
			bindValue(DomUtils.getAttributeValue(element, valueAttributeName), executionContext, new Fragment(element));
		}
	}

	public void visitAfter(Element element, ExecutionContext executionContext)
			throws SmooksException {
		if(!isAttribute) {
			bindValue(DomUtils.getAllText(element, false), executionContext, new Fragment(element));
		}
	}

	public void visitBefore(SAXElement element,
			ExecutionContext executionContext) throws SmooksException,
			IOException {
		if(isAttribute) {
			bindValue(SAXUtil.getAttribute(valueAttributeName, element.getAttributes()), executionContext, new Fragment(element));
		} else {
            // Turn on Text Accumulation...
            element.accumulateText();
		}
	}

	public void visitAfter(SAXElement element, ExecutionContext executionContext)
			throws SmooksException, IOException {
		if(!isAttribute) {
			bindValue(element.getTextContent(), executionContext, new Fragment(element));
		}
	}

	private void bindValue(String dataString, ExecutionContext executionContext, Fragment source) {
		Object valueObj = decodeDataString(dataString, executionContext);

		BeanContext beanContext = executionContext.getBeanContext();

		if(valueObj == null) {
			beanContext.removeBean(beanId, source);
		} else {
			beanContext.addBean(beanId, valueObj, source);
		}
	}

	public Set<? extends Object> getProducts() {
		return CollectionsUtil.toSet(beanIdName);
	}

	private Object decodeDataString(String dataString, ExecutionContext executionContext) throws DataDecodeException {
        if((dataString == null || dataString.length() == 0) && defaultValue != null) {
        	if(defaultValue.equals("null")) {
        		return null;
        	}
            dataString = defaultValue;
        }

        try {
            return getDecoder(executionContext).decode(dataString);
        } catch(DataDecodeException e) {
            throw new DataDecodeException("Failed to decode the value '" + dataString + "' for the bean id '" + beanIdName +"'.", e);
        }
    }

	private DataDecoder getDecoder(ExecutionContext executionContext) throws DataDecodeException {
		if(decoder == null) {
			@SuppressWarnings("unchecked")
			List decoders = executionContext.getDeliveryConfig().getObjects("decoder:" + typeAlias);

	        if (decoders == null || decoders.isEmpty()) {
	            decoder = DataDecoder.Factory.create(typeAlias);
	        } else if (!(decoders.get(0) instanceof DataDecoder)) {
	            throw new DataDecodeException("Configured decoder '" + typeAlias + ":" + decoders.get(0).getClass().getName() + "' is not an instance of " + DataDecoder.class.getName());
	        } else {
	            decoder = (DataDecoder) decoders.get(0);
	        }
		}
        return decoder;
    }

}