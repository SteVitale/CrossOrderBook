public class DailyHistory {
    public int openPrice;
    public int closePrice;
    public int maxPrice;
    public int minPrice;
    private boolean new_day;

    public DailyHistory(){
        openPrice = -1;
        closePrice = -1;
        maxPrice = -1;
        minPrice = -1;
        new_day = true;
    }

    void update(int price){
        if(new_day){
            openPrice = price;
            closePrice = price;
            maxPrice = price;
            minPrice = price;
            new_day = false;
        }else {
            closePrice = price;
            if(price > maxPrice) maxPrice = price;
            if(price < minPrice) minPrice = price;
        }
    }
}
