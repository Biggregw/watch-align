package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model profiles for watches that recur frequently in RepTimeQC posts.
 *
 * The catalog deliberately separates model identity from QC capabilities. A
 * profile can participate in the common round/indexed geometry engine, expose a
 * date/cyclops location, and optionally provide an exact official product page
 * for automatic reference discovery. Models without a stable official image
 * route still get on-device QC and can use a user-supplied genuine reference.
 */
final class ModelCatalog {
    enum GeometryMode { ROUND_INDEXED, VISUAL_ONLY }

    static final class Profile {
        final String code;
        final String label;
        final String brand;
        final GeometryMode geometryMode;
        final int dateHour;              // 0 = no date, otherwise 1..12
        final boolean cyclops;
        final double dateRadiusRatio;    // dial-radius fraction for expected aperture centre
        final String officialPage;       // null when automatic exact-model discovery is not configured

        Profile(String code, String label, String brand, GeometryMode geometryMode,
                int dateHour, boolean cyclops, double dateRadiusRatio, String officialPage) {
            this.code=code; this.label=label; this.brand=brand; this.geometryMode=geometryMode;
            this.dateHour=dateHour; this.cyclops=cyclops; this.dateRadiusRatio=dateRadiusRatio;
            this.officialPage=officialPage;
        }

        boolean hasDate(){ return dateHour>=1 && dateHour<=12; }
        boolean supportsAutoReference(){ return officialPage!=null && !officialPage.isEmpty(); }
        @Override public String toString(){ return label; }
    }

    private static final List<Profile> ALL;
    private static final Map<String,Profile> BY_CODE;

    static {
        List<Profile> p=new ArrayList<>();

        // Rolex sports / everyday models that dominate RepTimeQC traffic.
        add(p,"126710BLNR","Rolex GMT-Master II 126710BLNR · Batman/Batgirl","Rolex",GeometryMode.ROUND_INDEXED,3,true,0.69,"https://www.rolex.com/watches/gmt-master-ii/m126710blnr-0002");
        add(p,"126710BLRO","Rolex GMT-Master II 126710BLRO · Pepsi","Rolex",GeometryMode.ROUND_INDEXED,3,true,0.69,null);
        add(p,"126710GRNR","Rolex GMT-Master II 126710GRNR · Bruce Wayne","Rolex",GeometryMode.ROUND_INDEXED,3,true,0.69,null);
        add(p,"126720VTNR","Rolex GMT-Master II 126720VTNR · Sprite","Rolex",GeometryMode.ROUND_INDEXED,9,true,0.69,null);
        add(p,"124060","Rolex Submariner 124060 · No-Date","Rolex",GeometryMode.ROUND_INDEXED,0,false,0.0,"https://www.rolex.com/watches/submariner/m124060-0001");
        add(p,"126610LN","Rolex Submariner Date 126610LN","Rolex",GeometryMode.ROUND_INDEXED,3,true,0.69,null);
        add(p,"126610LV","Rolex Submariner Date 126610LV · Starbucks","Rolex",GeometryMode.ROUND_INDEXED,3,true,0.69,null);
        add(p,"116610LN","Rolex Submariner Date 116610LN","Rolex",GeometryMode.ROUND_INDEXED,3,true,0.69,null);
        add(p,"126500LN","Rolex Cosmograph Daytona 126500LN","Rolex",GeometryMode.ROUND_INDEXED,0,false,0.0,null);
        add(p,"116500LN","Rolex Cosmograph Daytona 116500LN","Rolex",GeometryMode.ROUND_INDEXED,0,false,0.0,null);
        add(p,"126334","Rolex Datejust 41 126334","Rolex",GeometryMode.ROUND_INDEXED,3,true,0.69,null);
        add(p,"126234","Rolex Datejust 36 126234","Rolex",GeometryMode.ROUND_INDEXED,3,true,0.69,null);
        add(p,"228238","Rolex Day-Date 40 228238","Rolex",GeometryMode.ROUND_INDEXED,3,true,0.69,null);
        add(p,"126622","Rolex Yacht-Master 40 126622","Rolex",GeometryMode.ROUND_INDEXED,3,true,0.69,null);
        add(p,"124270","Rolex Explorer 36 124270","Rolex",GeometryMode.ROUND_INDEXED,0,false,0.0,null);
        add(p,"224270","Rolex Explorer 40 224270","Rolex",GeometryMode.ROUND_INDEXED,0,false,0.0,null);
        add(p,"124300","Rolex Oyster Perpetual 41 124300","Rolex",GeometryMode.ROUND_INDEXED,0,false,0.0,null);

        // Omega models frequently checked for index/date alignment.
        add(p,"OMEGA-SMP300","Omega Seamaster Diver 300M 42mm","Omega",GeometryMode.ROUND_INDEXED,6,false,0.64,null);
        add(p,"OMEGA-AT38","Omega Aqua Terra 150M 38mm","Omega",GeometryMode.ROUND_INDEXED,6,false,0.62,null);
        add(p,"OMEGA-AT41","Omega Aqua Terra 150M 41mm","Omega",GeometryMode.ROUND_INDEXED,6,false,0.62,null);
        add(p,"OMEGA-PO","Omega Seamaster Planet Ocean","Omega",GeometryMode.ROUND_INDEXED,3,false,0.67,null);

        // AP / Patek / Tudor / Vacheron round indexed models commonly submitted for QC.
        add(p,"AP-15510","Audemars Piguet Royal Oak 15510","Audemars Piguet",GeometryMode.ROUND_INDEXED,3,false,0.67,null);
        add(p,"AP-15500","Audemars Piguet Royal Oak 15500","Audemars Piguet",GeometryMode.ROUND_INDEXED,3,false,0.67,null);
        add(p,"PP-5711","Patek Philippe Nautilus 5711","Patek Philippe",GeometryMode.ROUND_INDEXED,3,false,0.67,null);
        add(p,"PP-5811","Patek Philippe Nautilus 5811","Patek Philippe",GeometryMode.ROUND_INDEXED,3,false,0.67,null);
        add(p,"PP-5167","Patek Philippe Aquanaut 5167","Patek Philippe",GeometryMode.ROUND_INDEXED,3,false,0.67,null);
        add(p,"TUDOR-BB58","Tudor Black Bay Fifty-Eight","Tudor",GeometryMode.ROUND_INDEXED,0,false,0.0,null);
        add(p,"TUDOR-BB54","Tudor Black Bay 54","Tudor",GeometryMode.ROUND_INDEXED,0,false,0.0,null);
        add(p,"TUDOR-BBGMT","Tudor Black Bay GMT","Tudor",GeometryMode.ROUND_INDEXED,3,false,0.67,null);
        add(p,"TUDOR-P39","Tudor Pelagos 39","Tudor",GeometryMode.ROUND_INDEXED,0,false,0.0,null);
        add(p,"VC-4500V","Vacheron Constantin Overseas 4500V","Vacheron Constantin",GeometryMode.ROUND_INDEXED,3,false,0.67,null);

        // These are common QC submissions but their dial/case geometry is not circular-index based.
        // They are included so the app can present the correct model-specific visual checklist rather
        // than pretending the radial marker engine is applicable.
        add(p,"CARTIER-SANTOS-M","Cartier Santos Medium","Cartier",GeometryMode.VISUAL_ONLY,0,false,0.0,null);
        add(p,"CARTIER-SANTOS-L","Cartier Santos Large","Cartier",GeometryMode.VISUAL_ONLY,6,false,0.61,null);
        add(p,"CARTIER-TANK","Cartier Tank Must / Tank Solo","Cartier",GeometryMode.VISUAL_ONLY,0,false,0.0,null);

        ALL=Collections.unmodifiableList(p);
        Map<String,Profile> m=new LinkedHashMap<>();
        for(Profile x:p) {
            if(m.put(x.code,x)!=null) throw new IllegalStateException("Duplicate model code: "+x.code);
        }
        BY_CODE=Collections.unmodifiableMap(m);
    }

    private static void add(List<Profile> p,String code,String label,String brand,GeometryMode mode,
                            int dateHour,boolean cyclops,double dateRadiusRatio,String officialPage) {
        p.add(new Profile(code,label,brand,mode,dateHour,cyclops,dateRadiusRatio,officialPage));
    }

    static List<Profile> all(){ return ALL; }
    static Profile byCode(String code){ return BY_CODE.get(code); }
    static Profile require(String code){
        Profile p=byCode(code);
        if(p==null) throw new IllegalArgumentException("Unsupported model: "+code);
        return p;
    }
    static String[] labels(){
        String[] out=new String[ALL.size()];
        for(int i=0;i<ALL.size();i++) out[i]=ALL.get(i).label;
        return out;
    }
    static Profile at(int index){ return ALL.get(index); }

    private ModelCatalog(){}
}
