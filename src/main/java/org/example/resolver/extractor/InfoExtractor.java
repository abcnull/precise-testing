package org.example.resolver.extractor;

public interface InfoExtractor<T, S> {
    T extract(S source);
}