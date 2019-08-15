package com.atlassian.bitbucket.jenkins.internal.client.paging;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.stream.Stream;

import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.convertToElementStream;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketPageStreamTest {

    @Mock
    private NextPageFetcher nextPageFetcher;

    @Test
    public void testPageStream() {
        BitbucketPage<Integer> firstPage = new BitbucketPage<>();
        firstPage.setValues(asList(1, 2));

        BitbucketPage<Integer> secondPage = new BitbucketPage<>();
        secondPage.setValues(asList(3, 4));

        BitbucketPage<Integer> lastPage = new BitbucketPage<>();
        lastPage.setValues(asList(5, 6));
        lastPage.setLastPage(true);

        when(nextPageFetcher.next(firstPage)).thenReturn(secondPage);
        when(nextPageFetcher.next(secondPage)).thenReturn(lastPage);

        Stream<BitbucketPage<Integer>> stream = BitbucketPageStream.toStream(firstPage, nextPageFetcher);

        assertThat(convertToElementStream(stream).collect(toList()), contains(1, 2, 3, 4, 5, 6));
    }

    @Test
    public void testSinglePage() {
        BitbucketPage<Integer> firstPage = new BitbucketPage<>();
        firstPage.setValues(asList(1, 2));
        firstPage.setLastPage(true);

        Stream<BitbucketPage<Integer>> stream = BitbucketPageStream.toStream(firstPage, nextPageFetcher);

        assertThat(convertToElementStream(stream).collect(toList()), contains(1, 2));
        verify(nextPageFetcher.next(firstPage), never());
    }
}