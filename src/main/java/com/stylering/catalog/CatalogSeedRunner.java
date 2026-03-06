package com.stylering.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CatalogSeedRunner implements ApplicationRunner {

    private final CatalogItemRepository catalogItemRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean seedEnabled;

    public CatalogSeedRunner(
            CatalogItemRepository catalogItemRepository,
            @Value("${catalog.seed.enabled:true}") boolean seedEnabled
    ) {
        this.catalogItemRepository = catalogItemRepository;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        List<CatalogItem> seedItems = buildItems();
        List<CatalogItem> existingItems = catalogItemRepository.findAll();

        Set<String> existingKeys = new HashSet<>();
        for (CatalogItem existing : existingItems) {
            existingKeys.add(itemKey(existing.getType(), existing.getBrand(), existing.getName()));
        }

        List<CatalogItem> missing = new ArrayList<>();
        for (CatalogItem seedItem : seedItems) {
            String key = itemKey(seedItem.getType(), seedItem.getBrand(), seedItem.getName());
            if (!existingKeys.contains(key)) {
                missing.add(seedItem);
            }
        }

        if (!missing.isEmpty()) {
            catalogItemRepository.saveAll(missing);
        }
    }

    private List<CatalogItem> buildItems() {
        List<CatalogItem> items = new ArrayList<>();

        // ── TOP (25) ──────────────────────────────────────────────────────────
        items.add(item(CatalogItemType.TOP, "베이직 오버핏 반팔 티셔츠", "무신사스탠다드",
                "19000-29000", tags("casual", "white", "overfit", "daily", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "스트라이프 보트넥 티셔츠", "무신사스탠다드",
                "25000-35000", tags("casual", "navy", "regular", "daily", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "아치 로고 반팔 티셔츠", "커버낫",
                "35000-45000", tags("street", "black", "regular", "campus", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "코어 크루넥 긴팔 티셔츠", "커버낫",
                "45000-55000", tags("casual", "beige", "overfit", "daily", "cotton"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.TOP, "캠퍼스 후드 스웨트셔츠", "커버낫",
                "65000-79000", tags("street", "grey", "overfit", "campus", "cotton"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.TOP, "플라워 그래픽 반팔 티셔츠", "마르디메크르디",
                "69000-89000", tags("casual", "white", "regular", "date", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "피그먼트 다이 티셔츠", "마르디메크르디",
                "79000-89000", tags("minimal", "beige", "overfit", "daily", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "클린핏 링넥 티셔츠", "COS",
                "49000-59000", tags("minimal", "white", "slim", "office", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "헤비 코튼 박시 티셔츠", "COS",
                "59000-69000", tags("minimal", "black", "overfit", "daily", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "드라이 스트레치 폴로 셔츠", "유니클로",
                "29900-39900", tags("preppy", "navy", "regular", "office", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "린넨 블렌드 오버핏 셔츠", "유니클로",
                "39900-49900", tags("casual", "beige", "overfit", "daily", "linen"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "포플린 레귤러 핏 셔츠", "자라",
                "45900-55900", tags("minimal", "white", "regular", "office", "cotton"), "MIXED", "SS"));
        items.add(item(CatalogItemType.TOP, "오버사이즈 스트라이프 셔츠", "자라",
                "55900-65900", tags("casual", "blue", "overfit", "date", "cotton"), "MIXED", "SS"));
        items.add(item(CatalogItemType.TOP, "폴로 랄프로렌 슬림핏 피케 셔츠", "폴로 랄프로렌",
                "89000-109000", tags("preppy", "navy", "slim", "campus", "cotton"), "MIXED", "SS"));
        items.add(item(CatalogItemType.TOP, "니트 베스트 슬리브리스", "앤더슨벨",
                "89000-119000", tags("minimal", "beige", "regular", "date", "wool"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.TOP, "크롭 후드 집업", "앤더슨벨",
                "129000-159000", tags("street", "black", "overfit", "date", "cotton"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.TOP, "드라이-EX 크루넥 티셔츠", "유니클로",
                "19900-29900", tags("sporty", "grey", "regular", "daily", "nylon"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "빈티지 워싱 그래픽 티셔츠", "커버낫",
                "49000-59000", tags("vintage", "grey", "overfit", "campus", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "스우시 반팔 티셔츠", "나이키",
                "35000-45000", tags("sporty", "black", "regular", "daily", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "클럽하우스 테크 플리스 탑", "나이키",
                "69000-89000", tags("sporty", "navy", "regular", "outdoor", "nylon"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.TOP, "트레포일 반팔 티셔츠", "아디다스",
                "35000-45000", tags("street", "white", "regular", "campus", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "스탠 스미스 그래픽 티셔츠", "아디다스",
                "45000-55000", tags("street", "green", "regular", "daily", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.TOP, "크루넥 울 스웨터", "COS",
                "89000-119000", tags("minimal", "grey", "regular", "office", "wool"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.TOP, "오버핏 체크 플란넬 셔츠", "자라",
                "55900-69900", tags("casual", "brown", "overfit", "daily", "cotton"), "MIXED", "FW"));
        items.add(item(CatalogItemType.TOP, "에센셜 스트라이프 긴팔 티셔츠", "무신사스탠다드",
                "29000-39000", tags("casual", "navy", "regular", "daily", "cotton"), "UNISEX", "FW"));

        // ── PANTS (20) ───────────────────────────────────────────────────────
        items.add(item(CatalogItemType.PANTS, "베이직 슬림 데님 팬츠", "무신사스탠다드",
                "49000-69000", tags("casual", "blue", "slim", "daily", "denim"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.PANTS, "와이드 크롭 데님 팬츠", "무신사스탠다드",
                "59000-79000", tags("casual", "black", "wide", "campus", "denim"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.PANTS, "코튼 와이드 트라우저", "무신사스탠다드",
                "49000-65000", tags("minimal", "beige", "wide", "office", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.PANTS, "501 오리지널 청바지", "리바이스",
                "89000-109000", tags("vintage", "blue", "regular", "daily", "denim"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.PANTS, "512 슬림 테이퍼드 데님", "리바이스",
                "89000-109000", tags("casual", "black", "slim", "daily", "denim"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.PANTS, "클래식 치노 팬츠", "폴로 랄프로렌",
                "99000-129000", tags("preppy", "beige", "slim", "campus", "cotton"), "MIXED", "SS"));
        items.add(item(CatalogItemType.PANTS, "스트레이트 울 팬츠", "COS",
                "99000-129000", tags("minimal", "grey", "regular", "office", "wool"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.PANTS, "와이드 레그 트라우저", "자라",
                "55900-79900", tags("minimal", "black", "wide", "office", "cotton"), "MIXED", "SS"));
        items.add(item(CatalogItemType.PANTS, "조거 카고 팬츠", "커버낫",
                "65000-89000", tags("street", "olive", "relaxed", "campus", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.PANTS, "리플렉티브 트랙 팬츠", "나이키",
                "69000-89000", tags("sporty", "black", "regular", "outdoor", "nylon"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.PANTS, "테크팩 조거 팬츠", "아디다스",
                "69000-89000", tags("sporty", "navy", "relaxed", "outdoor", "nylon"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.PANTS, "빈티지 와이드 데님 팬츠", "앤더슨벨",
                "129000-159000", tags("vintage", "blue", "wide", "date", "denim"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.PANTS, "하이웨이스트 와이드 데님", "마르디메크르디",
                "99000-129000", tags("casual", "blue", "wide", "date", "denim"), "MIXED", "SS"));
        items.add(item(CatalogItemType.PANTS, "체크 울 슬랙스", "자라",
                "65900-89900", tags("formal", "grey", "slim", "office", "wool"), "MIXED", "FW"));
        items.add(item(CatalogItemType.PANTS, "울 블렌드 와이드 팬츠", "COS",
                "119000-149000", tags("minimal", "brown", "wide", "office", "wool"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.PANTS, "페이디드 스키니 데님", "자라",
                "55900-75900", tags("casual", "grey", "slim", "daily", "denim"), "MIXED", "SS"));
        items.add(item(CatalogItemType.PANTS, "코듀로이 와이드 팬츠", "무신사스탠다드",
                "55000-75000", tags("vintage", "brown", "wide", "campus", "cotton"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.PANTS, "플리스 트랙 팬츠", "나이키",
                "69000-89000", tags("sporty", "grey", "relaxed", "daily", "cotton"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.PANTS, "오버핏 쇼츠 팬츠", "커버낫",
                "45000-59000", tags("street", "black", "wide", "campus", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.PANTS, "슬림 스트레이트 치노", "유니클로",
                "39900-49900", tags("casual", "beige", "slim", "daily", "cotton"), "UNISEX", "SS"));

        // ── SHOES (20) ───────────────────────────────────────────────────────
        items.add(item(CatalogItemType.SHOES, "에어포스 1 '07", "나이키",
                "109000-129000", tags("street", "white", "regular", "daily", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "에어맥스 90", "나이키",
                "139000-169000", tags("sporty", "grey", "regular", "daily", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "덩크 로우", "나이키",
                "119000-149000", tags("street", "black", "regular", "campus", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "990v6", "뉴발란스",
                "179000-219000", tags("casual", "grey", "regular", "daily", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "530 스니커즈", "뉴발란스",
                "109000-139000", tags("casual", "white", "regular", "campus", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "2002R", "뉴발란스",
                "149000-179000", tags("casual", "beige", "regular", "daily", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "스탠 스미스", "아디다스",
                "99000-119000", tags("minimal", "white", "regular", "daily", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "삼바 OG", "아디다스",
                "109000-129000", tags("vintage", "white", "regular", "campus", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "가젤", "아디다스",
                "99000-119000", tags("casual", "navy", "regular", "daily", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "척 70 하이탑", "컨버스",
                "89000-109000", tags("street", "black", "regular", "campus", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "척 테일러 올스타 OX", "컨버스",
                "79000-99000", tags("casual", "white", "regular", "daily", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "1460 8홀 부츠", "닥터마틴",
                "199000-249000", tags("street", "black", "regular", "campus", ""), "UNISEX", "FW"));
        items.add(item(CatalogItemType.SHOES, "1461 옥스포드 슈즈", "닥터마틴",
                "169000-209000", tags("street", "black", "regular", "daily", ""), "UNISEX", "FW"));
        items.add(item(CatalogItemType.SHOES, "리더 로우탑 스니커즈", "아디다스",
                "89000-109000", tags("sporty", "white", "regular", "daily", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "클라우드 X 3", "뉴발란스",
                "149000-179000", tags("sporty", "white", "regular", "outdoor", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "자라 레더 더비 슈즈", "자라",
                "79900-99900", tags("formal", "black", "regular", "office", ""), "MIXED", "FW"));
        items.add(item(CatalogItemType.SHOES, "유니클로 스트레치 슬립온", "유니클로",
                "39900-49900", tags("casual", "black", "regular", "daily", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "척 70 로우 빈티지", "컨버스",
                "89000-109000", tags("vintage", "beige", "regular", "campus", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "에어 리프트", "나이키",
                "149000-179000", tags("sporty", "black", "regular", "outdoor", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.SHOES, "울트라부스트 22", "아디다스",
                "179000-219000", tags("sporty", "black", "regular", "outdoor", ""), "UNISEX", "SS"));

        // ── OUTER (20) ───────────────────────────────────────────────────────
        items.add(item(CatalogItemType.OUTER, "오버핏 캐시미어 울 코트", "앤더슨벨",
                "359000-429000", tags("minimal", "beige", "overfit", "date", "wool"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "더블 브레스티드 롱 코트", "앤더슨벨",
                "329000-399000", tags("formal", "black", "regular", "date", "wool"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "알파 SV 재킷", "아크테릭스",
                "899000-1099000", tags("sporty", "black", "regular", "outdoor", "nylon"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "아톰 LT 후디", "아크테릭스",
                "489000-589000", tags("sporty", "navy", "regular", "outdoor", "nylon"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "로고 오버핏 후드 집업", "커버낫",
                "89000-119000", tags("street", "black", "overfit", "campus", "cotton"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "스타디움 코치 자켓", "커버낫",
                "99000-129000", tags("vintage", "navy", "regular", "campus", "nylon"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "아웃도어 마운틴 파카", "코오롱스포츠",
                "279000-349000", tags("sporty", "olive", "regular", "outdoor", "nylon"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "윈드스토퍼 소프트쉘 재킷", "코오롱스포츠",
                "199000-259000", tags("sporty", "black", "regular", "outdoor", "nylon"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "아이스 클라이밍 다운 파카", "디스커버리",
                "249000-319000", tags("sporty", "navy", "regular", "outdoor", "nylon"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "본파이어 구스다운 점퍼", "디스커버리",
                "289000-369000", tags("sporty", "black", "regular", "travel", "nylon"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "클린핏 오버사이즈 블레이저", "COS",
                "189000-239000", tags("minimal", "grey", "overfit", "office", "wool"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "테일러드 더블 코트", "COS",
                "299000-369000", tags("minimal", "beige", "regular", "office", "wool"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "에디션 수트 재킷", "자라",
                "99900-129900", tags("formal", "black", "regular", "office", ""), "MIXED", "FW"));
        items.add(item(CatalogItemType.OUTER, "레더 바이커 자켓", "자라",
                "119900-159900", tags("street", "black", "regular", "date", ""), "MIXED", "FW"));
        items.add(item(CatalogItemType.OUTER, "빅포니 더블 페이스 코트", "폴로 랄프로렌",
                "399000-499000", tags("preppy", "navy", "regular", "date", "wool"), "MIXED", "FW"));
        items.add(item(CatalogItemType.OUTER, "마르디 오버핏 짧은 트렌치 코트", "마르디메크르디",
                "229000-279000", tags("casual", "beige", "overfit", "date", "cotton"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "울 체크 오버핏 코트", "무신사스탠다드",
                "129000-169000", tags("casual", "brown", "overfit", "daily", "wool"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "빈티지 나일론 아노락", "무신사스탠다드",
                "79000-99000", tags("street", "olive", "overfit", "campus", "nylon"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "테크 팩 윈드런너", "나이키",
                "129000-169000", tags("sporty", "black", "regular", "outdoor", "nylon"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.OUTER, "에센셜스 다운 재킷", "뉴발란스",
                "149000-199000", tags("sporty", "navy", "regular", "daily", "nylon"), "UNISEX", "FW"));

        // ── ACCESSORY (15) ────────────────────────────────────────────────────
        items.add(item(CatalogItemType.ACCESSORY, "레버스 리버서블 버킷 햇", "무신사스탠다드",
                "25000-35000", tags("casual", "black", "regular", "daily", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "울 비니", "무신사스탠다드",
                "19000-25000", tags("casual", "grey", "regular", "daily", "wool"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.ACCESSORY, "로고 볼캡", "커버낫",
                "39000-49000", tags("street", "black", "regular", "campus", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "스크립트 로고 버킷 햇", "커버낫",
                "45000-55000", tags("street", "beige", "regular", "daily", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "플라워 크로스백", "마르디메크르디",
                "129000-159000", tags("casual", "white", "regular", "date", "cotton"), "MIXED", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "빅 로고 숄더백", "커버낫",
                "89000-109000", tags("street", "black", "regular", "daily", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "미니 숄더 백", "자라",
                "59900-79900", tags("minimal", "beige", "regular", "date", ""), "MIXED", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "와이드 레더 벨트", "COS",
                "59000-79000", tags("minimal", "black", "regular", "office", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "울 스카프", "유니클로",
                "29900-39900", tags("casual", "grey", "regular", "daily", "wool"), "UNISEX", "FW"));
        items.add(item(CatalogItemType.ACCESSORY, "클래식 캔버스 토트백", "무신사스탠다드",
                "29000-39000", tags("casual", "beige", "regular", "campus", "cotton"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "트레일 런닝 백팩", "나이키",
                "79000-99000", tags("sporty", "black", "regular", "outdoor", "nylon"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "에센셜 웨이스트백", "아디다스",
                "45000-59000", tags("sporty", "black", "regular", "travel", "nylon"), "UNISEX", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "뉴에라 9FORTY 볼캡", "뉴발란스",
                "49000-59000", tags("casual", "navy", "regular", "campus", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "메쉬 볼캡", "아디다스",
                "39000-49000", tags("sporty", "white", "regular", "outdoor", ""), "UNISEX", "SS"));
        items.add(item(CatalogItemType.ACCESSORY, "빈티지 클러치백", "앤더슨벨",
                "159000-199000", tags("minimal", "black", "regular", "date", ""), "UNISEX", "SS"));

        return items;
    }

    private CatalogItem item(
            CatalogItemType type, String name, String brand,
            String priceRange, String tagsJson,
            String gender, String season
    ) {
        String encoded = brand.replace(" ", "+");
        String nameEncoded = name.replace(" ", "+");
        String productUrl = "https://www.musinsa.com/search/musinsa/goods?q=" + encoded + "+" + nameEncoded;
        return new CatalogItem(type, name, brand, priceRange, tagsJson, gender, season, productUrl);
    }

    private String tags(String style, String color, String fit, String occasion, String material) {
        try {
            List<String> list = new ArrayList<>();
            if (!style.isEmpty())    list.add(style);
            if (!color.isEmpty())    list.add(color);
            if (!fit.isEmpty())      list.add(fit);
            if (!occasion.isEmpty()) list.add(occasion);
            if (!material.isEmpty()) list.add(material);
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write tags json", ex);
        }
    }

    private String itemKey(CatalogItemType type, String brand, String name) {
        return type.name() + "|" + brand + "|" + name;
    }
}
