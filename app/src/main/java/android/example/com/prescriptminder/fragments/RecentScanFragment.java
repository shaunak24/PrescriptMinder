package android.example.com.prescriptminder.fragments;


import android.content.Context;
import android.content.SharedPreferences;
import android.example.com.prescriptminder.R;
import android.example.com.prescriptminder.utils.Constants;
import android.example.com.prescriptminder.utils.Medicines;
import android.example.com.prescriptminder.utils.MedicinesAdapter;
import android.example.com.prescriptminder.utils.OkHttpUtils;
import android.example.com.prescriptminder.utils.QRCodeUtil;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * A simple {@link Fragment} subclass.
 */
public class RecentScanFragment extends Fragment {

    private static RecentScanFragment recentScanFragment;
    private String JSON;
    private RecyclerView recyclerView;
    private MedicinesAdapter adapter;
    private ArrayList<Medicines> medicinesArrayList;
    private TextView patient_name;
    private TextView doctor_name;
    private TextView pres_id;
    private TextView date;
    private TextView time;
    private ImageView qrcode;
    private Button play;

    public RecentScanFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

//        MainActivity.navigation.setSelectedItemId(R.id.navigation_recent_scan);

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_recent_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable Bundle savedInstanceState) {

        patient_name = view.findViewById(R.id.patient_name);
        doctor_name = view.findViewById(R.id.doctor_name);
        pres_id = view.findViewById(R.id.prescription_id);
        date = view.findViewById(R.id.date);
        time = view.findViewById(R.id.time);
        qrcode = view.findViewById(R.id.qrcode);
        play = view.findViewById(R.id.play_audio);
        medicinesArrayList = new ArrayList<>();

        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        SharedPreferences sharedPref = Objects.requireNonNull(getActivity()).getPreferences(Context.MODE_PRIVATE);
                        String email = sharedPref.getString("email", "null");
                        RequestBody requestBody = new MultipartBody.Builder().addFormDataPart("mail", email).build();
                        Call call = OkHttpUtils.sendHttpPostRequest("http://10.194.168.231:8000/prescript/view/37/", requestBody);
                        call.enqueue(new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                e.printStackTrace();
                            }

                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                File file = convertToFile(response.body().byteStream());
                                Log.e("Received File", file.getPath());
                                String path = Environment.getExternalStorageDirectory() + "/" + file.getName();
                                MediaPlayer mediaPlayer = new MediaPlayer();
                                mediaPlayer.setDataSource(path);
                                mediaPlayer.prepare();
                                mediaPlayer.start();
                            }
                        });
                    }
                });
                thread.start();
            }
        });

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    getMedicineInfo();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();

        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MedicinesAdapter(getContext(), medicinesArrayList);
        recyclerView.setAdapter(adapter);
    }

    private File convertToFile(InputStream byteStream) throws IOException{
        InputStream inputStream = byteStream;

        File receivedFile = new File(Environment.getExternalStorageDirectory(), "Hi");
        OutputStream outputStream = new FileOutputStream(receivedFile);
        try {
            byte[] buffer = new byte[4 * 1024];
            int read;

            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        } finally {
            outputStream.close();
        }
        return receivedFile;
    }

    private void getMedicineInfo() throws IOException, JSONException {
        String url = Constants.BASE_URL + "prescript/view/1/";
        Log.e("URL", url);
        SharedPreferences sharedPref = Objects.requireNonNull(getActivity()).getPreferences(Context.MODE_PRIVATE);
        String email = sharedPref.getString("email", "null");
        OkHttpClient client = new OkHttpClient();
        RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("mail", email).build();
        Request request = new Request.Builder().url(url).post(requestBody).build();
        Response response = client.newCall(request).execute();
        JSON = response.body().string();

        if (!response.isSuccessful())
            throw new IOException("Response code : " + response);
        Log.e("Response", JSON);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    parseJSON(JSON);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void parseJSON(String json) throws JSONException {

        JSONObject jsonObject = new JSONObject(json);
        if (jsonObject.getString("status").equals("ok")) {
            setInitialDetails(jsonObject);
            JSONArray medicines = jsonObject.getJSONArray("medicine");
            for (int i = 0; i < medicines.length(); i++) {
                JSONObject object = medicines.getJSONObject(i);
                medicinesArrayList.add(new Medicines(object.getString("name"), object.getString("note"),
                        object.getString("schedule")));
            }
            adapter.notifyDataSetChanged();
        } else {
            showToast(jsonObject.getString("status"));
        }
    }

    private void setInitialDetails(JSONObject jsonObject) throws JSONException {
        patient_name.setText(jsonObject.getString("patient_name"));
        doctor_name.setText(jsonObject.getString("doctor_name"));
        pres_id.setText(jsonObject.getString("id"));
        date.setText(jsonObject.getString("date"));
        time.setText(jsonObject.getString("time"));
        qrcode.setImageBitmap(QRCodeUtil.encodeAsBitmap(jsonObject.getString("audio_name"), 100, 100));
    }

    public static RecentScanFragment getRecentScanFragment() {
        if (recentScanFragment == null)
            recentScanFragment = new RecentScanFragment();
        return recentScanFragment;
    }

    private void showToast(String msg) {
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
    }
}
