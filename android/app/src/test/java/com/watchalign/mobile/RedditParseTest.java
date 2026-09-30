package com.watchalign.mobile;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** alpha67: Reddit search replies (synthetic, shaped like the real API) are turned into photo links and tag guesses. */
public class RedditParseTest {
    private static final String LISTING="{\"kind\":\"Listing\",\"data\":{\"after\":\"t3_ccc\",\"children\":["
            // Gallery post: two valid images and one still processing, in gallery order.
            +"{\"kind\":\"t3\",\"data\":{\"id\":\"aaa\",\"title\":\"[QC] VSF 126710BLNR jubilee\",\"permalink\":\"/r/RepTimeQC/comments/aaa/qc/\","
            +"\"created_utc\":1790000000.0,\"is_gallery\":true,\"url\":\"https://www.reddit.com/gallery/aaa\","
            +"\"gallery_data\":{\"items\":[{\"media_id\":\"m2\"},{\"media_id\":\"m1\"},{\"media_id\":\"m3\"}]},"
            +"\"media_metadata\":{\"m1\":{\"status\":\"valid\",\"e\":\"Image\",\"s\":{\"u\":\"https://preview.redd.it/m1.jpg?width=3024&amp;format=pjpg\"}},"
            +"\"m2\":{\"status\":\"valid\",\"e\":\"Image\",\"s\":{\"u\":\"https://preview.redd.it/m2.jpg?width=3024\"}},"
            +"\"m3\":{\"status\":\"unprocessed\"}}}},"
            // Direct i.redd.it image.
            +"{\"kind\":\"t3\",\"data\":{\"id\":\"bbb\",\"title\":\"Clean Pepsi GMT QC\",\"permalink\":\"/r/RepTimeQC/comments/bbb/x/\",\"url\":\"https://i.redd.it/abc123.jpeg\"}},"
            // Text post with an Imgur album and a single Imgur image in the body; a video link is ignored.
            +"{\"kind\":\"t3\",\"data\":{\"id\":\"ccc\",\"title\":\"GMF sprite, thoughts?\",\"permalink\":\"/r/RepTimeQC/comments/ccc/x/\",\"url\":\"https://www.reddit.com/r/RepTimeQC/comments/ccc/x/\","
            +"\"selftext\":\"Album: https://imgur.com/a/qc-pics-AbCdE12 and https://imgur.com/XyZ9876 plus (https://i.imgur.com/Vid1234.gifv)\"}},"
            // A comment kind is skipped.
            +"{\"kind\":\"t1\",\"data\":{\"id\":\"zzz\"}}"
            +"]}}";

    @Test public void listingGivesPhotosInOrder(){
        RedditClient.Page p=RedditClient.parseListing(LISTING);
        assertEquals("t3_ccc",p.after);
        assertEquals(3,p.posts.size());
        RedditClient.Post a=p.posts.get(0);
        assertEquals("aaa",a.id);assertEquals("https://www.reddit.com/r/RepTimeQC/comments/aaa/qc/",a.permalink);
        assertEquals(Arrays.asList("https://preview.redd.it/m2.jpg?width=3024","https://preview.redd.it/m1.jpg?width=3024&format=pjpg"),a.images);
        assertEquals(Arrays.asList("https://i.redd.it/abc123.jpeg"),p.posts.get(1).images);
        RedditClient.Post c=p.posts.get(2);
        assertEquals(Arrays.asList("https://i.imgur.com/XyZ9876.jpg"),c.images);
        assertEquals(Arrays.asList("AbCdE12"),c.imgurAlbums);
    }

    @Test public void linkSorting(){
        Set<String> i=new LinkedHashSet<>(),a=new LinkedHashSet<>();
        RedditClient.addLink("https://imgur.com/gallery/Qwe12",i,a);
        RedditClient.addLink("https://i.imgur.com/Abc12.png",i,a);
        RedditClient.addLink("https://i.imgur.com/Mov12.mp4",i,a);
        RedditClient.addLink("https://example.com/watch.JPG",i,a);
        RedditClient.addLink("https://example.com/page",i,a);
        RedditClient.addLink("https://v.redd.it/xyz",i,a);
        assertEquals(Arrays.asList("https://i.imgur.com/Abc12.png","https://example.com/watch.JPG"),Arrays.asList(i.toArray()));
        assertEquals(Arrays.asList("Qwe12"),Arrays.asList(a.toArray()));
    }

    @Test public void crosspostAndPreviewFallback(){
        String j="{\"data\":{\"children\":[{\"kind\":\"t3\",\"data\":{\"id\":\"x\",\"title\":\"t\",\"url\":\"/r/a/comments/y\","
                +"\"crosspost_parent_list\":[{\"url\":\"https://i.redd.it/orig.jpg\"}]}},"
                +"{\"kind\":\"t3\",\"data\":{\"id\":\"y\",\"title\":\"t\",\"url\":\"https://somehost.example/view/123\",\"post_hint\":\"image\","
                +"\"preview\":{\"images\":[{\"source\":{\"url\":\"https://preview.redd.it/p.jpg?a=1&amp;b=2\"}}]}}}]}}";
        RedditClient.Page p=RedditClient.parseListing(j);
        assertEquals(Arrays.asList("https://i.redd.it/orig.jpg"),p.posts.get(0).images);
        assertEquals(Arrays.asList("https://preview.redd.it/p.jpg?a=1&b=2"),p.posts.get(1).images);
        assertNull(p.after);
    }

    @Test public void imgurAlbumSkipsGifsAndVideo(){
        List<String> l=RedditClient.parseImgurAlbum("{\"data\":[{\"type\":\"image/jpeg\",\"link\":\"https://i.imgur.com/a1.jpg\"},"
                +"{\"type\":\"image/gif\",\"link\":\"https://i.imgur.com/a2.gif\"},{\"type\":\"video/mp4\",\"link\":\"https://i.imgur.com/a3.mp4\"},"
                +"{\"type\":\"image/png\",\"link\":\"https://i.imgur.com/a4.png\"}],\"success\":true}");
        assertEquals(Arrays.asList("https://i.imgur.com/a1.jpg","https://i.imgur.com/a4.png"),l);
    }

    @Test public void miniJsonHandlesEscapesAndNumbers(){
        Object o=MiniJson.parse("{\"a\":\"x\\\"y\\u00e9\\n\",\"b\":[1,-2.5e1,true,null],\"c\":{}}");
        assertEquals("x\"yé\n",MiniJson.str(o,"a"));
        assertEquals(-25.0,(Double)MiniJson.at(o,"b",1),0);
        assertEquals(Boolean.TRUE,MiniJson.at(o,"b",2));
        assertNull(MiniJson.at(o,"b",3));assertNull(MiniJson.at(o,"b",9));assertNull(MiniJson.str(o,"c","d"));
        try{MiniJson.parse("{\"a\":1} x");fail();}catch(IllegalArgumentException expected){}
    }

    private static void tags(String title,String model,String factory,boolean gmt,boolean mixed){
        TitleTags.Guess g=TitleTags.guess(title);
        assertEquals(title,model,g.model);assertEquals(title,factory,g.factory);assertEquals(title,gmt,g.gmt);assertEquals(title,mixed,g.mixed);
    }

    @Test public void titleGuesses(){
        tags("[QC] VSF 126710BLNR jubilee","126710BLNR","VSF",true,false);
        tags("Clean Pepsi GMT QC","126710BLRO","Clean",true,false);
        tags("GMF sprite, thoughts?","126720VTNR","GMF",true,false);
        tags("126710 bruce wayne from Rich","126710GRNR","Rich",true,false);
        tags("C+ GMT 126711 root beer","126711CHNR","C+",true,false);
        tags("Everose root beer ARF","126715CHNR","ARF",true,false);
        tags("VS factory batman vs gen comparison","126710BLNR","VSF",true,true);
        tags("GMT QC please","","",true,false);
        tags("Clean Submariner QC","","Clean",false,false);
        tags("126710 BLRO next to my real one","126710BLRO","",true,true);
    }
}
