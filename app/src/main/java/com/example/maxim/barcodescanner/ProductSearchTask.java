package com.example.maxim.barcodescanner;

import android.os.AsyncTask;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

class Product {
    public String code;
    public String name;
    public Float price;
    public String imageUrl;
}

public class ProductSearchTask extends AsyncTask {
    private String url = "https://rozetka.com.ua/search/autocomplete/?text=";
    private static final String TAG = "ProductSearch";

    @Override
    protected void onProgressUpdate(Object[] values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected Object doInBackground(Object[] objects) {
        return find(objects[0].toString());
    }

    @Override
    protected void onPostExecute(Object o) {
        super.onPostExecute(o);
        MainActivity.loading.setVisibility(View.INVISIBLE);
        Product product = (Product) o;
        MainActivity.showProduct(product);
    }

    public Product find(String barcode) {
        Product product = new Product();
        product.code = barcode;
        Log.d(TAG, String.format("find product with barcode %s", barcode));

        try {
            URL endpoint = new URL(url + barcode);
            HttpsURLConnection connection = (HttpsURLConnection) endpoint.openConnection();

            connection.setRequestProperty("User-Agent", "barcodescannerapp-client");
            connection.setRequestProperty("Accept", "application/json");

            if (connection.getResponseCode() == 200) {
                InputStream data = connection.getInputStream();
                InputStreamReader reader = new InputStreamReader(data, "UTF-8");

                JsonReader jsonReader = new JsonReader(reader);
                jsonReader.beginObject();

                while (jsonReader.hasNext()) {
                    final String name = jsonReader.nextName();

                    if(name.equals("content")) {
                        jsonReader.beginObject();

                        while(jsonReader.hasNext()) {
                            final String innerName = jsonReader.nextName();

                            if(innerName.equals("records")) {
                                jsonReader.beginObject();

                                while(jsonReader.hasNext()) {
                                    final String innerName2 = jsonReader.nextName();

                                    if(innerName2.equals("goods")) {
                                        jsonReader.beginArray();
                                        while( jsonReader.hasNext() ) {
                                            jsonReader.beginObject();

                                            while(jsonReader.hasNext()) {
                                                final String prop = jsonReader.nextName();

                                                if (prop.equals("title") || prop.equals("image") || prop.equals("price")) {
                                                    final String val = jsonReader.nextString();

                                                    if (prop.equals("title")) {
                                                        product.name = val;
                                                    }
                                                    if (prop.equals("image")) {
                                                        product.imageUrl = val;
                                                    }
                                                    if (prop.equals("price")) {
                                                        product.price = Float.valueOf(val.trim()).floatValue();
                                                    }
                                                }
                                                else {
                                                    jsonReader.skipValue();
                                                }
                                            }
                                            jsonReader.endObject();
                                        }
                                        jsonReader.endArray();
                                    } else {
                                        jsonReader.skipValue();
                                    }
                                }

                                jsonReader.endObject();
                            } else {
                                jsonReader.skipValue();
                            }
                        }
                        jsonReader.endObject();
                    } else {
                        jsonReader.skipValue();
                    }
                }
                jsonReader.endObject();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return product;
    }
}
