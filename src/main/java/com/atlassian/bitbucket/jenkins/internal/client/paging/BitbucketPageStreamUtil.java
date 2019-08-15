package com.atlassian.bitbucket.jenkins.internal.client.paging;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BitbucketPageStreamUtil {

    public static <T> Stream<BitbucketPage<T>> toStream(BitbucketPage<T> firstPage, NextPageFetcher nextPageFetcher) {
        return StreamSupport.stream(pageIterable(firstPage, nextPageFetcher).spliterator(), false);
    }

    private static <T> Iterable<BitbucketPage<T>> pageIterable(BitbucketPage<T> firstPage,
                                                               NextPageFetcher nextPageFetcher) {
        return () -> new PageIterator<>(nextPageFetcher, firstPage);
    }

    private static class PageIterator<T> implements Iterator<BitbucketPage<T>> {

        private final NextPageFetcher nextPageFetcher;
        private BitbucketPage<T> currentPage;

        PageIterator(NextPageFetcher nextPageFetcher,
                     BitbucketPage<T> firstPage) {
            this.nextPageFetcher = nextPageFetcher;
            this.currentPage = firstPage;
        }

        @Override
        public boolean hasNext() {
            return currentPage != null;
        }

        @Override
        public BitbucketPage<T> next() {
            if (currentPage == null) {
                throw new IllegalStateException();
            }
            BitbucketPage<T> result;
            if (currentPage.isLastPage()) {
                result = currentPage;
                currentPage = null;
            } else {
                result = currentPage;
                currentPage = nextPageFetcher.next(currentPage);
            }
            return result;
        }
    }
}
