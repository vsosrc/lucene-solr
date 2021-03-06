package org.apache.solr.handler.component;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.solr.BaseDistributedSearchTestCase;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.BeforeClass;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Iterator;

/**
 * Test for QueryComponent's distributed querying
 *
 * @see org.apache.solr.handler.component.QueryComponent
 */
public class DistributedExpandComponentTest extends BaseDistributedSearchTestCase {

  public DistributedExpandComponentTest() {
    fixShardCount = true;
    shardCount = 3;
    stress = 0;
  }

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    initCore("solrconfig-collapseqparser.xml", "schema11.xml");
  }

  @Override
  public void doTest() throws Exception {
    del("*:*");

    index_specific(0,"id","1", "term_s", "YYYY", "group_s", "group1", "test_ti", "5",  "test_tl", "10", "test_tf", "2000");
    index_specific(0,"id","2", "term_s", "YYYY", "group_s", "group1", "test_ti", "50", "test_tl", "100", "test_tf", "200");
    index_specific(1,"id","5", "term_s", "YYYY", "group_s", "group2", "test_ti", "4",  "test_tl", "10", "test_tf", "2000");
    index_specific(1,"id","6", "term_s", "YYYY", "group_s", "group2", "test_ti", "10", "test_tl", "100", "test_tf", "200");
    index_specific(0,"id","7", "term_s", "YYYY", "group_s", "group1", "test_ti", "1",  "test_tl", "100000", "test_tf", "2000");
    index_specific(1,"id","8", "term_s", "YYYY", "group_s", "group2", "test_ti", "2",  "test_tl", "100000", "test_tf", "200");
    index_specific(2,"id","9", "term_s", "YYYY", "group_s", "group3", "test_ti", "1000", "test_tl", "1005", "test_tf", "3000");
    index_specific(2, "id", "10", "term_s", "YYYY", "group_s", "group3", "test_ti", "1500", "test_tl", "1001", "test_tf", "3200");
    index_specific(2,"id", "11",  "term_s", "YYYY", "group_s", "group3", "test_ti", "1300", "test_tl", "1002", "test_tf", "3300");
    index_specific(1,"id","12", "term_s", "YYYY", "group_s", "group4", "test_ti", "15",  "test_tl", "10", "test_tf", "2000");
    index_specific(1,"id","13", "term_s", "YYYY", "group_s", "group4", "test_ti", "16",  "test_tl", "9", "test_tf", "2000");
    index_specific(1,"id","14", "term_s", "YYYY", "group_s", "group4", "test_ti", "1",  "test_tl", "20", "test_tf", "2000");


    commit();


    handle.put("explain", SKIPVAL);
    handle.put("QTime", SKIPVAL);
    handle.put("timestamp", SKIPVAL);
    handle.put("score", SKIPVAL);
    handle.put("wt", SKIP);
    handle.put("distrib", SKIP);
    handle.put("shards.qt", SKIP);
    handle.put("shards", SKIP);
    handle.put("q", SKIP);
    handle.put("maxScore", SKIPVAL);
    handle.put("_version_", SKIP);

    query("q", "*:*", "fq", "{!collapse field=group_s}", "defType", "edismax", "bf", "field(test_ti)", "expand", "true", "fl","*,score");
    query("q", "*:*", "fq", "{!collapse field=group_s}", "defType", "edismax", "bf", "field(test_ti)", "expand", "true", "expand.sort", "test_tl desc", "fl","*,score");
    query("q", "*:*", "fq", "{!collapse field=group_s}", "defType", "edismax", "bf", "field(test_ti)", "expand", "true", "expand.sort", "test_tl desc", "expand.rows", "1", "fl","*,score");
    //Test no expand results
    query("q", "test_ti:5", "fq", "{!collapse field=group_s}", "defType", "edismax", "bf", "field(test_ti)", "expand", "true", "expand.sort", "test_tl desc", "expand.rows", "1", "fl","*,score");
    //Test zero results
    query("q", "test_ti:5434343", "fq", "{!collapse field=group_s}", "defType", "edismax", "bf", "field(test_ti)", "expand", "true", "expand.sort", "test_tl desc", "expand.rows", "1", "fl","*,score");
    //Test page 2
    query("q", "*:*", "start","1", "rows", "1", "fq", "{!collapse field=group_s}", "defType", "edismax", "bf", "field(test_ti)", "expand", "true", "fl","*,score");


    //First basic test case.
    ModifiableSolrParams params = new ModifiableSolrParams();
    params.add("q", "*:*");
    params.add("fq", "{!collapse field=group_s}");
    params.add("defType", "edismax");
    params.add("bf", "field(test_ti)");
    params.add("expand", "true");

    setDistributedParams(params);
    QueryResponse rsp = queryServer(params);
    Map<String, SolrDocumentList> results = rsp.getExpandedResults();
    assertExpandGroups(results, "group1","group2", "group3", "group4");
    assertExpandGroupCountAndOrder("group1", 2, results, "1.0", "7.0");
    assertExpandGroupCountAndOrder("group2", 2, results, "5.0", "8.0");
    assertExpandGroupCountAndOrder("group3", 2, results, "11.0", "9.0");
    assertExpandGroupCountAndOrder("group4", 2, results, "12.0", "14.0");


    //Test expand.sort

    params = new ModifiableSolrParams();
    params.add("q", "*:*");
    params.add("fq", "{!collapse field=group_s}");
    params.add("defType", "edismax");
    params.add("bf", "field(test_ti)");
    params.add("expand", "true");
    params.add("expand.sort", "test_tl desc");
    setDistributedParams(params);
    rsp = queryServer(params);
    results = rsp.getExpandedResults();
    assertExpandGroups(results, "group1","group2", "group3", "group4");
    assertExpandGroupCountAndOrder("group1", 2, results, "7.0", "1.0");
    assertExpandGroupCountAndOrder("group2", 2, results, "8.0", "5.0");
    assertExpandGroupCountAndOrder("group3", 2, results, "9.0", "11.0");
    assertExpandGroupCountAndOrder("group4", 2, results, "14.0", "12.0");


    //Test expand.rows

    params = new ModifiableSolrParams();
    params.add("q", "*:*");
    params.add("fq", "{!collapse field=group_s}");
    params.add("defType", "edismax");
    params.add("bf", "field(test_ti)");
    params.add("expand", "true");
    params.add("expand.sort", "test_tl desc");
    params.add("expand.rows", "1");
    setDistributedParams(params);
    rsp = queryServer(params);
    results = rsp.getExpandedResults();
    assertExpandGroups(results, "group1","group2", "group3", "group4");
    assertExpandGroupCountAndOrder("group1", 1, results, "7.0");
    assertExpandGroupCountAndOrder("group2", 1, results, "8.0");
    assertExpandGroupCountAndOrder("group3", 1, results, "9.0");
    assertExpandGroupCountAndOrder("group4", 1, results, "14.0");

  }

  private void assertExpandGroups(Map<String, SolrDocumentList> expandedResults, String... groups) throws Exception {
    for(int i=0; i<groups.length; i++) {
      if(!expandedResults.containsKey(groups[i])) {
        throw new Exception("Expanded Group Not Found:"+groups[i]+", Found:"+exportGroups(expandedResults));
      }
    }
  }

  private String exportGroups(Map<String, SolrDocumentList> groups) {
    StringBuilder buf = new StringBuilder();
    Iterator<String> it = groups.keySet().iterator();
    while(it.hasNext()) {
      String group = it.next();
      buf.append(group);
      if(it.hasNext()) {
        buf.append(",");
      }
    }
    return buf.toString();
  }

  private void assertExpandGroupCountAndOrder(String group, int count, Map<String, SolrDocumentList>expandedResults, String... docs) throws Exception {
    SolrDocumentList results = expandedResults.get(group);
    if(results == null) {
      throw new Exception("Group Not Found:"+group);
    }

    if(results.size() != count) {
      throw new Exception("Expected Count "+results.size()+" Not Found:"+count);
    }

    for(int i=0; i<docs.length;i++) {
      String id = docs[i];
      SolrDocument doc = results.get(i);
      if(!doc.getFieldValue("id").toString().equals(id)) {
        throw new Exception("Id not in results or out of order:"+id+"!="+doc.getFieldValue("id"));
      }
    }
  }
}
