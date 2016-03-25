package es.uma.muii.apdm.ImageProcessingNative;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MatrixActivity extends AppCompatActivity {
    Button okButton;
    TextView[] matrix;
    TextView divisor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matrix);


        matrix =  new TextView[9];
        matrix[0] = (TextView) findViewById(R.id.matrix0);
        matrix[1] = (TextView) findViewById(R.id.matrix1);
        matrix[2] = (TextView) findViewById(R.id.matrix2);
        matrix[3] = (TextView) findViewById(R.id.matrix3);
        matrix[4] = (TextView) findViewById(R.id.matrix4);
        matrix[5] = (TextView) findViewById(R.id.matrix5);
        matrix[6] = (TextView) findViewById(R.id.matrix6);
        matrix[7] = (TextView) findViewById(R.id.matrix7);
        matrix[8] = (TextView) findViewById(R.id.matrix8);
        divisor = (TextView) findViewById(R.id.divisor);


        Bundle params = getIntent().getExtras();
        divisor.setText(String.valueOf(params.get("divisor")));
        int[] matrixIn = (int[]) params.get("matrix");
        for(int i = 0; i < 9; i ++)
        {
            matrix[i].setText(String.valueOf(matrixIn[i]));
        }
    }

    public void validar(View v)
    {
        Intent returnIntent = new Intent();
        int[] matrixRet = new int[9];
        int divisorRet = Integer.valueOf(divisor.getText().toString());
        for(int i = 0; i < 9; i ++)
        {
            matrixRet[i] = Integer.valueOf(matrix[i].getText().toString());
        }

        Log.d("Entrada", String.valueOf(matrixRet[0]));

        returnIntent.putExtra("matrix", matrixRet);
        returnIntent.putExtra("divisor", divisorRet);
        setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }

}
