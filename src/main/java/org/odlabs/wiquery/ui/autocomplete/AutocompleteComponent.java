/*
 * Copyright (c) 2009 WiQuery team
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.odlabs.wiquery.ui.autocomplete;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.markup.html.form.FormComponentPanel;
import org.apache.wicket.markup.html.form.HiddenField;
import org.apache.wicket.markup.html.resources.JavascriptResourceReference;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.string.Strings;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.odlabs.wiquery.core.commons.WiQueryResourceManager;
import org.odlabs.wiquery.core.javascript.JsQuery;
import org.odlabs.wiquery.core.javascript.JsScope;
import org.odlabs.wiquery.core.javascript.JsStatement;
import org.odlabs.wiquery.core.javascript.JsUtils;
import org.odlabs.wiquery.core.javascript.helper.EventsHelper;
import org.odlabs.wiquery.ui.core.JsScopeUiEvent;

/**
 * $Id$
 * <p>
 * Creates an autocomplete UI component which will bind on a Wicket model
 * </p>
 * @author Julien Roche
 * @param <T> The model object type
 * @since 1.1
 */
public abstract class AutocompleteComponent<T> extends FormComponentPanel<T> {
	// Constants
	/**	Constant of serialization */
	private static final long serialVersionUID = -3377109382248062940L;
	
	/** Constant of wiQuery Autocomplete resource */
	public static final JavascriptResourceReference WIQUERY_AUTOCOMPLETE_JS = 
		new JavascriptResourceReference(
				AutocompleteComponent.class, 
				"wiquery-autocomplete.min.js");
	
	// Wicket components
	private final Autocomplete<String> autocompleteField;
	private final HiddenField<Integer> autocompleteHidden;
	
	// Properties
	private final IModel<List<? extends T>> list;
	private final Map<Integer, T> mapIdToModel;

	/**
	 * Constructor
	 * @param id Wicket identifiant
	 * @param model Model of the default value
	 * @param list List of possibles values
	 */
	public AutocompleteComponent(String id, final IModel<T> model, final IModel<List<? extends T>> list) {
		super(id, model);
		setOutputMarkupPlaceholderTag(true);
		
		this.list = list;
		mapIdToModel = new HashMap<Integer, T>();
		
		autocompleteHidden = new HiddenField<Integer>("autocompleteHidden", new Model<Integer>(), Integer.class);
		autocompleteHidden.setOutputMarkupId(true);
		add(autocompleteHidden);
		
		autocompleteField = new InnerAutocomplete<String>("autocompleteField", new Model<String>());
		add(autocompleteField);
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.apache.wicket.markup.html.form.FormComponent#convertInput()
	 */
	@Override
	protected final void convertInput() {
		Integer valueId = autocompleteHidden.getConvertedInput();
		String input = autocompleteField.getConvertedInput();
		
		if(valueId == null && Strings.isEmpty(input)){
			setConvertedInput(null);
			
		} else if(valueId == null){
			setConvertedInput(getValueOnSearchFail(input));
			
		} else {
			setConvertedInput(mapIdToModel.get(valueId));
		}
	}

	/**
	 * @return the autocomplete field
	 */
	public Autocomplete<String> getAutocompleteField() {
		return autocompleteField;
	}

	/**
	 * @return Hidden field storing the identifiant of the Wicket model
	 */
	public HiddenField<Integer> getAutocompleteHidden() {
		return autocompleteHidden;
	}
	
	/**
	 * Method called when the input is not empty and the search failed
	 * @param input Current input
	 * @return a new value
	 */
	public abstract T getValueOnSearchFail(String input);
	
	/**
	 * Create an {@link AutocompleteJson}
	 * @param id
	 * @param obj
	 * @return a new instance of {@link AutocompleteJson}
	 */
	protected AutocompleteJson newAutocompleteJson(int id, T obj) {
		return new AutocompleteJson(id, obj == null ? "" : obj.toString());
	}
	
	/**
	 * Inner {@link Autocomplete}
	 * @author Julien Roche
	 *
	 */
	private class InnerAutocomplete<E> extends Autocomplete<E> {
		// Constants
		/**	Constant of serialization */
		private static final long serialVersionUID = -6129719872925080990L;

		/**
		 * Constructor
		 * @param id Wicket identifiant
		 * @param model Model
		 */
		public InnerAutocomplete(String id, IModel<E> model) {
			super(id, model);
			super.setSelectEvent(
					JsScopeUiEvent.quickScope(
							"$.ui.autocomplete.wiquery.selectEvent(ui, " 
							+ JsUtils.quotes(autocompleteHidden.getMarkupId()) + ");"));
		}

		/**
		 * {@inheritDoc}
		 * @see org.odlabs.wiquery.ui.autocomplete.Autocomplete#contribute(org.odlabs.wiquery.core.commons.WiQueryResourceManager)
		 */
		@Override
		public void contribute(WiQueryResourceManager wiQueryResourceManager) {
			super.contribute(wiQueryResourceManager);
			wiQueryResourceManager.addJavaScriptResource(WIQUERY_AUTOCOMPLETE_JS);
		}

		/**
		 * {@inheritDoc}
		 * @see org.apache.wicket.markup.html.form.AbstractTextComponent#onBeforeRender()
		 */
		@Override
		protected void onBeforeRender() {
			StringWriter sw = new StringWriter();
			mapIdToModel.clear();
			
			try {
				JsonGenerator gen = new JsonFactory().createJsonGenerator(sw);
				
				List<Object> json = new ArrayList<Object>();
				T defaultValue = AutocompleteComponent.this.getModelObject();
				AutocompleteJson value = null;
				Integer index = 0;
				
				for(T obj : AutocompleteComponent.this.list.getObject()){
					index++;
					value = newAutocompleteJson(index, obj);
					json.add(value);
					mapIdToModel.put(value.getValueId(), obj);
					
					if(obj.equals(defaultValue)){
						setDefaultModelObject(value.getLabel());
						autocompleteHidden.setModelObject(value.getValueId());
					}
				}
				
				new ObjectMapper().writeValue(gen, json);
				
			} catch (IOException e) {
				throw new WicketRuntimeException(e);
			}
			
			getOptions().put("source", sw.toString());
			
			super.onBeforeRender();
		}

		/**
		 * {@inheritDoc}
		 * @see org.odlabs.wiquery.ui.autocomplete.Autocomplete#setCloseEvent(org.odlabs.wiquery.ui.core.JsScopeUiEvent)
		 */
		@Override
		public Autocomplete<E> setCloseEvent(JsScopeUiEvent close) {
			throw new WicketRuntimeException("You can't define the close event");
		}

		/**
		 * {@inheritDoc}
		 * @see org.odlabs.wiquery.ui.autocomplete.Autocomplete#setSelectEvent(org.odlabs.wiquery.ui.core.JsScopeUiEvent)
		 */
		@Override
		public Autocomplete<E> setSelectEvent(JsScopeUiEvent select) {
			throw new WicketRuntimeException("You can't define the select event");
		}

		/**
		 * {@inheritDoc}
		 * @see org.odlabs.wiquery.ui.autocomplete.Autocomplete#setSource(org.odlabs.wiquery.ui.autocomplete.AutocompleteSource)
		 */
		@Override
		public Autocomplete<E> setSource(AutocompleteSource source) {
			throw new WicketRuntimeException("You can't define the source");
		}

		/**
		 * {@inheritDoc}
		 * @see org.odlabs.wiquery.ui.autocomplete.Autocomplete#statement()
		 */
		@Override
		public JsStatement statement() {
			JsStatement jsStatement = super.statement();
			jsStatement.chain(
					EventsHelper.blur(
							JsScope.quickScope(
									"$.ui.autocomplete.wiquery.closeEvent(" 
									+ new JsQuery(this).$().render(false)
									+ ", " 
									+ JsUtils.quotes(autocompleteHidden.getMarkupId()) + ");")));
			
			return jsStatement;
		}
	}
}