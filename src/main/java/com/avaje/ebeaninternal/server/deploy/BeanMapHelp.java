package com.avaje.ebeaninternal.server.deploy;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.Query;
import com.avaje.ebean.Transaction;
import com.avaje.ebean.bean.BeanCollection;
import com.avaje.ebean.bean.BeanCollectionAdd;
import com.avaje.ebean.bean.BeanCollectionLoader;
import com.avaje.ebean.common.BeanMap;
import com.avaje.ebeaninternal.server.text.json.WriteJsonContext;

/**
 * Helper specifically for dealing with Maps.
 */
public final class BeanMapHelp<T> implements BeanCollectionHelp<T> {

	private final BeanPropertyAssocMany<T> many;
	private final BeanDescriptor<T> targetDescriptor;
	private final BeanProperty beanProperty;
	private BeanCollectionLoader loader;
	//private final String mapKey;
	
	/**
	 * When created for a given query that will return a map.
	 */
	public BeanMapHelp(BeanDescriptor<T> targetDescriptor, String mapKey) {
		this(null, targetDescriptor, mapKey);
	}

	public BeanMapHelp(BeanPropertyAssocMany<T> many){
		this(many, many.getTargetDescriptor(), many.getMapKey());
	}
	
	/**
	 * When help is attached to a specific many property.
	 */
	private BeanMapHelp(BeanPropertyAssocMany<T> many, BeanDescriptor<T> targetDescriptor, String mapKey){
		this.many = many;
		this.targetDescriptor = targetDescriptor;
		//this.mapKey = mapKey;
		this.beanProperty = targetDescriptor.getBeanProperty(mapKey);
	}
	
	/**
	 * Return an iterator of the values.
	 */
    public Iterator<?> getIterator(Object collection) {
        return ((Map<?,?>) collection).values().iterator();
    }
	
	public void setLoader(BeanCollectionLoader loader){
		this.loader = loader;
	}

    @SuppressWarnings("unchecked")
	public BeanCollectionAdd getBeanCollectionAdd(Object bc, String mapKey) {
		
		if(mapKey == null){
			mapKey = many.getMapKey();
		}
		BeanProperty beanProp = targetDescriptor.getBeanProperty(mapKey);
		
		if (bc instanceof BeanMap<?,?>){
    		BeanMap<Object, Object> bm = (BeanMap<Object, Object>)bc;
    		Map<Object, Object> actualMap = bm.getActualMap();
    		if (actualMap == null){
    			actualMap = new LinkedHashMap<Object, Object>();
    			bm.setActualMap(actualMap);
    		}
    		return new Adder(beanProp, actualMap);
		
		} else if (bc instanceof Map<?,?>) {
            return new Adder(beanProp, (Map<Object, Object>)bc);		    
		
		} else {
            throw new RuntimeException("Unhandled type "+bc);
        }
	}

	static class Adder implements BeanCollectionAdd {
		
		private final BeanProperty beanProperty;
		
		private final Map<Object, Object> map;
		
		Adder(BeanProperty beanProperty, Map<Object, Object> map) {
			this.beanProperty = beanProperty;
			this.map = map;
		}
		
		public void addBean(Object bean) {
			Object keyValue = beanProperty.getValue(bean);
			map.put(keyValue, bean);
		}
	}

	@SuppressWarnings("rawtypes")
    public Object createEmpty(boolean vanilla) {
	  if (vanilla) {
	    return new LinkedHashMap();
	  }
		BeanMap beanMap = new BeanMap();
		if (many != null) {
		  beanMap.setModifyListening(many.getModifyListenMode());
		}
		return beanMap;
	}
	

  /**
   * Internal add bypassing any modify listening.
   */
	public void add(BeanCollection<?> collection, Object bean) {

		Object keyValue = beanProperty.getValueIntercept(bean);
		((BeanMap<?,?>) collection).internalPut(keyValue, bean);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public BeanCollection<T> createReference(Object parentBean, String propertyName) {

	  BeanMap beanMap = new BeanMap(loader, parentBean, propertyName);
    if (many != null) {
      beanMap.setModifyListening(many.getModifyListenMode());
    }
    return beanMap;
	}

	public void refresh(EbeanServer server, Query<?> query, Transaction t, Object parentBean) {
		BeanMap<?, ?> newBeanMap = (BeanMap<?, ?>) server.findMap(query, t);
		refresh(newBeanMap, parentBean);
	}
	
	public void refresh(BeanCollection<?> bc, Object parentBean) {

		BeanMap<?, ?> newBeanMap = (BeanMap<?, ?>) bc;
		Map<?, ?> current = (Map<?, ?>) many.getValue(parentBean);

		newBeanMap.setModifyListening(many.getModifyListenMode());
		if (current == null) {
			// the currentMap is null? Not really expecting this...
			many.setValue(parentBean, newBeanMap);

		} else if (current instanceof BeanMap<?,?>) {
			// normally this case, replace just the underlying list
			BeanMap<?, ?> currentBeanMap = (BeanMap<?, ?>) current;
			currentBeanMap.setActualMap(newBeanMap.getActualMap());
			currentBeanMap.setModifyListening(many.getModifyListenMode());

		} else {
			// replace the entire set
			many.setValue(parentBean, newBeanMap);
		}
	}

    public void jsonWrite(WriteJsonContext ctx, String name, Object collection, boolean explicitInclude) {
        
        Map<?,?> map;
        if (collection instanceof BeanCollection<?>){
            BeanMap<?,?> bc = (BeanMap<?,?>)collection;
            if (!bc.isPopulated()){
                if (explicitInclude){
                    // invoke lazy loading as collection 
                    // is explicitly included in the output
                    bc.size();
                } else {
                    return;
                }
            } 
            map = bc.getActualMap();
        } else {
            map = (Map<?,?>)collection;
        }
        
        int count = 0;
        ctx.beginAssocMany(name);
        Iterator<?> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Entry<?, ?> entry = (Entry<?, ?>)it.next();
            if (count++ > 0){
                ctx.appendComma();
            }
            //FIXME: json write map key ...
            Object detailBean = entry.getValue();
            targetDescriptor.jsonWrite(ctx, detailBean);
        }
        ctx.endAssocMany();      
    }

}
