package edu.ucsf.lava.core.dao;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import edu.ucsf.lava.core.auth.model.AuthUser;

public interface LavaDaoFilter extends Serializable {
	
	public static final int RESULT_COUNT_EMPTY = 0;
	
	public LavaDaoFilter setSort(String propertyName);
	public LavaDaoFilter addSort(String propertyName, boolean ascending);
	public LavaDaoFilter clearSort();
	public Map getSort();

	public LavaDaoFilter addDefaultSort(String propertyName, boolean ascending);
	public LavaDaoFilter clearDefaultSort();
	public Map getDefaultSort();

	public AuthUser getAuthUser();
	public void setAuthUser(AuthUser user);

	public LavaDaoFilter setContextFilter(String contextKey, String paramName, Object paramValue);
	public LavaDaoFilter setContextFilter(String contextKey, Map<String,Object> params);
	
	public LavaDaoFilter clearContextFilter(String contextKey);
	public Map<String,Map<String,Object>> getContextFilters();
	public Object getContextFilter(String contextKey);
	
	public LavaDaoFilter setAlias(String collectionName, String alias);
	public LavaDaoFilter clearAlias(String collectionName);
	public LavaDaoFilter clearAliases();
	public Map getAliases();
	public Object getAlias(String collectionName);
	
	public LavaDaoFilter setOuterAlias(String collectionName, String alias);
	public LavaDaoFilter clearOuterAlias(String collectionName);
	public LavaDaoFilter clearOuterAliases();
	public Map getOuterAliases();
	public Object getOuterAlias(String collectionName);
	
	public LavaDaoFilter setParam(String paramName, Object paramValue);
	public LavaDaoFilter clearParam(String paramName);
	public LavaDaoFilter clearParams();
	public Map getParams();
	public Object getParam(String paramName);
	public boolean paramsNotEqualTo(Map<String,Object> oldParams);
	
	public List <LavaDaoParam> getDaoParams();
	public void addDaoParam(LavaDaoParam param);
	public void clearDaoParams();
	public LavaDaoParam daoEqualityParam(String propertyName, Object param);
	public LavaDaoParam daoLikeParam(String propertyName, String pattern);
	public LavaDaoParam daoBetweenParam(String propertyName, Object lo, Object high);
	public LavaDaoParam daoNamedParam(String propertyName,Object param);
	public LavaDaoParam daoPositionalParam(String propertyName);
	public LavaDaoParam daoInParam(String propertyName, Object[] values);
	public LavaDaoParam daoNot(LavaDaoParam param);
	public LavaDaoParam daoOr(LavaDaoParam param, LavaDaoParam param2);
	public LavaDaoParam daoAnd(LavaDaoParam param, LavaDaoParam param2);
	public LavaDaoParam daoNull(String propertyName);
	public LavaDaoParam daoNotNull(String propertyName);
	public LavaDaoParam daoGreaterThanParam(String propertyName, Object value);
	public LavaDaoParam daoGreaterThanOrEqualParam(String propertyName, Object value);
	public LavaDaoParam daoLessThanParam(String propertyName, Object value);
	public LavaDaoParam daoLessThanOrEqualParam(String propertyName, Object value);
	
	//convenience function
	public LavaDaoFilter addIdDaoEqualityParam(Long id);
	
	public LavaDaoFilter setRows(int firstRowNum, int lastRowNum);
	public LavaDaoFilter clearRows();
	public int getFirstRowNum();
	public int getLastRowNum();
	public boolean rowSelectorsSet();
	public int rowSetSize();
	public LavaDaoFilter setResultsCount(int resultsCount);
	public int getResultsCount();
	public List<Object> getIdCache();
	public LavaDaoFilter setIdCache(List<Object> idCache);
	public LavaDaoFilter clearIdCache();
	public LavaDaoParam daoInIdCacheParam(int firstRow,int lastRow);
	public LavaDaoParam daoInIdCacheParam();
	

	
	
	public LavaDaoFilter addParamHandler(LavaParamHandler handler);
	public void clearParamHandlers();
	public void convertParamsToDaoParams();
	
	

	
	

}