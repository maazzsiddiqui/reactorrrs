package com.alchemain.rx.init;

import java.util.Iterator;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryStringQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.alchemain.rx.messages.ExecutionContext;
import com.alchemain.rx.utils.Constants;
import com.alchemain.rx.bus.JsonProvider;

public class SearchWrapper implements Constants {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private ElasticsearchClient searchClient;

    @Inject
    public SearchWrapper(ElasticsearchClient searchClient) {
        this.searchClient = searchClient;
    }

    public String indexObject(ExecutionContext context, String resource, String _id, JsonNode data) throws Exception {

        // The ES Java API doesn't take a JSON node directly, so we have to
        // stringify it.
        String dataAsString = JsonProvider.INSTANCE.getMapper().writeValueAsString(data);

        IndexResponse response = searchClient.index(i -> i
            .index(context.getTenant())
            .id(_id)
            .document(JsonData.fromJson(dataAsString))
        );
        return response.id();
    }

    public Boolean deleteObject(ExecutionContext context, String resource, String _id) throws Exception {

        DeleteResponse response = searchClient.delete(d -> d
            .index(context.getTenant())
            .id(_id)
        );
        return response.result().jsonValue().equals("deleted");
    }

    public JsonNode executeStringQuery(ExecutionContext context, JsonNode requestData) throws Exception {

        SearchResponse response = null;

        int offset = requestData.path(PAGING).get(OFFSET).asInt();
        int limit = requestData.path(PAGING).get(LIMIT).asInt();
        String query = requestData.path(DATA).get(QUERY).asText();

        String resource = null;
        if (requestData.path(DATA).has(RESOURCE)) {
            resource = requestData.path(DATA).get(RESOURCE).asText();
        }

        Query queryObj = Query.of(q -> q
            .queryString(QueryStringQuery.of(qs -> qs
                .query(query)
            ))
        );

        if (resource != null) {
            log.debug("Executing search query [{}] on tenant [{}] using type [{}]", query, context.getTenant(),
                    resource);
            response = searchClient.search(s -> s
                .index(context.getTenant())
                .query(queryObj)
                .sort(so -> so.field(f -> f.field(DISPLAY_NAME).order(SortOrder.Asc)))
                .from(offset)
                .size(limit)
            );
        } else {
            log.debug("Executing search query [{}] on tenant [{}] with no specified type", query,
                    context.getTenant());
            response = searchClient.search(s -> s
                .index(context.getTenant())
                .query(queryObj)
                .sort(so -> so.field(f -> f.field(DISPLAY_NAME).order(SortOrder.Asc)))
                .from(offset)
                .size(limit)
            );
        }

        return generatePagedResponse(response, offset, limit);

    }

    /*
     * This query is essentially used as an exact match, and the expectation is
     * that it will return a single result. If a "fuzzier" search is desired
     * (when there could possibly be multiple matches to a single search term),
     * use the executeSimpleQuery method above.
     */
    public JsonNode executeMatchQuery(ExecutionContext context, String resource, String field, String query)
            throws Exception {

        SearchResponse response = null;
        Query matchQuery = Query.of(q -> q
            .match(MatchQuery.of(m -> m
                .field(field)
                .query(query)
            ))
        );

        if (resource != null) {
            response = searchClient.search(s -> s
                .index(context.getTenant())
                .query(matchQuery)
            );
        } else {
            response = searchClient.search(s -> s
                .index(context.getTenant())
                .query(matchQuery)
            );
        }

        if (response.hits().total().value() != 1) {
            throw new Exception(String.format("Multiple matches found for an exact match query! field: %s, query: %s",
                    field, query));
        }

        List<Hit<JsonData>> hits = response.hits().hits();
        if (!hits.isEmpty()) {
            Hit<JsonData> hit = hits.get(0);
            return JsonProvider.INSTANCE.getMapper().readTree(hit.source().toString());
        } else {
            return JsonProvider.INSTANCE.getMapper().createObjectNode();
        }
    }

    private ObjectNode generatePagedResponse(SearchResponse matches, int offset, int limit) throws Exception {

        ObjectNode pagedResponse = JsonProvider.INSTANCE.getMapper().createObjectNode();
        ObjectNode pagingInfo = pagedResponse.putObject(PAGING);
        pagingInfo.put(OFFSET, offset);
        pagingInfo.put(LIMIT, limit);
        pagingInfo.put(TOTAL_COUNT, matches.hits().total().value());
        ArrayNode responseData = pagedResponse.putArray(DATA);

        List<Hit<JsonData>> hits = matches.hits().hits();
        for (Hit<JsonData> hit : hits) {
            responseData.add(JsonProvider.INSTANCE.getMapper().readTree(hit.source().toString()));
        }

        pagingInfo.put(COUNT, responseData.size());

        return pagedResponse;
    }
}
