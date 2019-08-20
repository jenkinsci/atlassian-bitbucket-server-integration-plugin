package com.atlassian.bitbucket.jenkins.internal.client.paging;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Provides a way to return Stream of page based on first page and {@link NextPageFetcher}.
 */
public class BitbucketPageStreamUtil {

    /**
     * Returns a Stream of Bitbucket Pages. {@link NextPageFetcher} provides a way for individual client to provide a way
     * to fetch next page.
     *
     * @param firstPage       First Page
     * @param nextPageFetcher Used for fetching next page
     * @param <T>             Type for Page
     * @return Stream of pages.
     */
    public static <T> Stream<BitbucketPage<T>> toStream(BitbucketPage<T> firstPage,
                                                        NextPageFetcher<T> nextPageFetcher) {
        return StreamSupport.stream(pageIterable(firstPage, nextPageFetcher).spliterator(), false);
    }

    private static <T> Iterable<BitbucketPage<T>> pageIterable(BitbucketPage<T> firstPage,
                                                               NextPageFetcher<T> nextPageFetcher) {
        return () -> new PageIterator<>(nextPageFetcher, firstPage);
    }

    private static class PageIterator<T> implements Iterator<BitbucketPage<T>> {

        private final NextPageFetcher<T> nextPageFetcher;
        private BitbucketPage<T> currentPage;

        PageIterator(NextPageFetcher<T> nextPageFetcher,
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
