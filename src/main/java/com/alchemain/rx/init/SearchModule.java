package com.alchemain.rx.init;

import java.net.InetAddress;
import java.net.UnknownHostException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.alchemain.rx.utils.PropertiesUtil;

public class SearchModule extends AbstractModule {

    private final static Logger log = LoggerFactory.getLogger(SearchModule.class);

    private final String CLIENT_HOSTS = "search.transport.hosts";
    private final String CLIENT_METHOD = "search.client.method";
    private final String CLUSTER_NAME = "search.cluster.name";

    @Override
    protected void configure() {
        bind(ElasticsearchClient.class).toInstance(esConnect());
        bind(SearchWrapper.class).in(Scopes.SINGLETON);
    }

    public ElasticsearchClient esConnect() {
        String transportMethod = PropertiesUtil.string(CLIENT_METHOD);
        String clusterName = PropertiesUtil.string(CLUSTER_NAME);
        if (clusterName == null)
            clusterName = "elasticsearch";

        log.trace("Creating ES Client:  cluster = {}, transport = {}", clusterName, transportMethod);

        String[] hosts = PropertiesUtil.string(CLIENT_HOSTS).split(",");
        HttpHost[] httpHosts = new HttpHost[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            String[] constituents = hosts[i].split(":");
            try {
                httpHosts[i] = new HttpHost(InetAddress.getByName(constituents[0]), Integer.parseInt(constituents[1]), "http");
            } catch (UnknownHostException e) {
                log.error("Failed to parse host address: {}", e.getMessage());
                httpHosts[i] = new HttpHost(constituents[0], Integer.parseInt(constituents[1]), "http");
            }
        }
        
        // Create the low-level client
        RestClient restClient = RestClient.builder(httpHosts).build();
        
        // Create the transport with a Jackson mapper
        ElasticsearchTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper());
        
        // And create the API client
        return new ElasticsearchClient(transport);
    }
}

