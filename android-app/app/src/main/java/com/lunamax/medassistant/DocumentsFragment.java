package com.lunamax.medassistant;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lunamax.medassistant.databinding.FragmentDocumentsBinding;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DocumentsFragment extends BaseFragment {
    private static final int REQUEST_FILE = 4101;
    private static final int REQUEST_CAMERA = 4102;
    private FragmentDocumentsBinding binding;
    private DocumentAdapter adapter;
    private final List<PendingSource> queue = new ArrayList<>();
    private int queueIndex;
    private long activeDocumentId;
    private File pendingCamera;
    private Uri cameraUri;
    private AlertDialog pendingDialog;

    void cancelPendingUi() {
        queue.clear();
        queueIndex = 0;
        activeDocumentId = 0;
        if (pendingDialog != null) {
            pendingDialog.dismiss();
            pendingDialog = null;
        }
    }

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle state) { binding=FragmentDocumentsBinding.inflate(inflater,container,false);return binding.getRoot(); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        adapter=new DocumentAdapter(this::onDocumentAction); binding.documentsList.setLayoutManager(new LinearLayoutManager(requireContext())); binding.documentsList.setAdapter(adapter);
        binding.documentImport.setOnClickListener(v->startPicker()); binding.documentCamera.setOnClickListener(v->startCamera());
        binding.documentSearch.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){refresh();}public void afterTextChanged(android.text.Editable e){}}); refresh();
    }
    @Override public void onResume(){super.onResume();if(binding!=null)refresh();}
    private void refresh(){String q=binding.documentSearch==null||binding.documentSearch.getText()==null?"":binding.documentSearch.getText().toString();List<LunaDatabase.DocumentRow> rows=database().documents(q);binding.documentsEmpty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);binding.documentsList.setVisibility(rows.isEmpty()?View.GONE:View.VISIBLE);adapter.submit(rows);}

    private void startPicker(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/pdf"}).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(intent,REQUEST_FILE);}
    private void startCamera(){if(ContextCompat.checkSelfPermission(requireContext(),Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA);return;}try{pendingCamera=privateFile("camera-"+System.currentTimeMillis()+".jpg");cameraUri=FileProvider.getUriForFile(requireContext(),requireContext().getPackageName()+".fileprovider",pendingCamera);Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,cameraUri).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(intent,REQUEST_CAMERA);}catch(Exception error){feedback("无法打开相机："+host().safeMessage(error));}}
    @Override public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==REQUEST_CAMERA&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)startCamera();else if(requestCode==REQUEST_CAMERA)feedback("没有相机权限，仍可从文件导入");}
    @Override public void onActivityResult(int requestCode,int resultCode,@Nullable Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=android.app.Activity.RESULT_OK)return;try{if(requestCode==REQUEST_CAMERA&&pendingCamera!=null){List<PendingSource> sources=new ArrayList<>();sources.add(new PendingSource(pendingCamera,normalizeImage(pendingCamera),"image/jpeg",1));confirmSources(sources);}else if(requestCode==REQUEST_FILE&&data!=null){List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){ClipData clip=data.getClipData();for(int i=0;i<clip.getItemCount();i++)uris.add(clip.getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());List<PendingSource> sources=new ArrayList<>();for(Uri uri:uris)sources.addAll(prepare(uri));confirmSources(sources);}}catch(Exception error){feedback("资料准备失败："+host().safeMessage(error));}}

    private List<PendingSource> prepare(Uri uri)throws Exception{File original=copyToPrivate(uri);String type=requireContext().getContentResolver().getType(uri);if(type==null)type="image/jpeg";if(type.toLowerCase(Locale.ROOT).contains("pdf"))return renderPdf(original);List<PendingSource> one=new ArrayList<>();one.add(new PendingSource(original,normalizeImage(original),type,1));return one;}
    private List<PendingSource> renderPdf(File original)throws Exception{List<PendingSource> result=new ArrayList<>();try(PdfRenderer renderer=new PdfRenderer(ParcelFileDescriptor.open(original,ParcelFileDescriptor.MODE_READ_ONLY))){for(int i=0;i<renderer.getPageCount();i++){PdfRenderer.Page page=renderer.openPage(i);Bitmap bitmap=Bitmap.createBitmap(page.getWidth(),page.getHeight(),Bitmap.Config.ARGB_8888);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);page.close();File compressed=privateFile("pdf-"+System.currentTimeMillis()+"-"+(i+1)+".jpg");writeBitmap(bitmap,compressed);bitmap.recycle();result.add(new PendingSource(original,compressed,"image/jpeg",i+1));}}return result;}
    private File normalizeImage(File original)throws Exception{Bitmap bitmap=BitmapFactory.decodeFile(original.getAbsolutePath());if(bitmap==null)throw new IllegalArgumentException("图片无法读取");try{int orientation=new ExifInterface(original.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);if(orientation!=ExifInterface.ORIENTATION_NORMAL){Matrix matrix=new Matrix();if(orientation==ExifInterface.ORIENTATION_ROTATE_90)matrix.postRotate(90);else if(orientation==ExifInterface.ORIENTATION_ROTATE_180)matrix.postRotate(180);else if(orientation==ExifInterface.ORIENTATION_ROTATE_270)matrix.postRotate(270);Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}}}catch(Exception ignored){}File compressed=privateFile("image-"+System.currentTimeMillis()+".jpg");writeBitmap(bitmap,compressed);bitmap.recycle();return compressed;}
    private void writeBitmap(Bitmap bitmap,File target)throws Exception{int max=2200;float scale=Math.min(1f,Math.min((float)max/bitmap.getWidth(),(float)max/bitmap.getHeight()));Bitmap output=scale<1f?Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*scale)),Math.max(1,Math.round(bitmap.getHeight()*scale)),true):bitmap;try(OutputStream out=new FileOutputStream(target)){if(!output.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IllegalStateException("图片压缩失败");}if(output!=bitmap)output.recycle();}
    private File copyToPrivate(Uri uri)throws Exception{File file=privateFile("import-"+System.currentTimeMillis());try(InputStream in=requireContext().getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);}return file;}
    private File privateFile(String name){File dir=new File(requireContext().getFilesDir(),"documents");if(!dir.exists())dir.mkdirs();return new File(dir,name);}
    private byte[] bytes(File file)throws Exception{if(file==null||!file.isFile()||file.length()>5_000_000)throw new IllegalArgumentException("上传图片不存在或过大");try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);return out.toByteArray();}}
    private String sha256(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)digest.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(Locale.US,"%02x",b));return s.toString();}

    private void confirmSources(List<PendingSource> sources){if(sources==null||sources.isEmpty()){feedback("没有可识别的图片");return;}long generation=vision().currentGeneration();long bytes=0;for(PendingSource source:sources)bytes+=source.compressed.length();ProviderProfile provider=vision().currentProvider();LinearLayout box=verticalForm();box.addView(infoLabel("将发送 "+sources.size()+" 页图片给 " + provider.displayName + " · " + provider.modelId + "。原始文件仍在应用私有目录。"));box.addView(infoLabel("压缩后约 "+Math.max(1,bytes/1024)+" KB；不会自动发送健康档案。"));new MaterialAlertDialogBuilder(requireContext()).setTitle("确认 AI 图片识别").setView(box).setNegativeButton("保存，稍后识别",(d,w)->{if(vision().isCurrent(generation))saveWaiting(sources);}).setPositiveButton(assistant().hasKey()?"发送给当前 AI":"先保存待识别",(d,w)->{if(!vision().isCurrent(generation))return;if(assistant().hasKey()){queue.clear();queue.addAll(sources);queueIndex=0;recognizeNext();}else saveWaiting(sources);}).show();}
    private void saveWaiting(List<PendingSource> sources){for(PendingSource source:sources){LunaDatabase.DocumentDraft draft=draft(source,"WAITING_KEY");database().addDocument(draft);}refresh();feedback(assistant().hasKey()?"已保存资料":"已保存为等待识别；配置 Key 后可重试");}
    private LunaDatabase.DocumentDraft draft(PendingSource source,String status){LunaDatabase.DocumentDraft d=new LunaDatabase.DocumentDraft();ProviderProfile provider=vision().currentProvider();d.uri=source.original.getAbsolutePath();d.mimeType=source.sourceMime;d.sha256=safeHash(source.original);d.pageNo=source.page;d.compressedUri=source.compressed.getAbsolutePath();d.compressedSha256=safeHash(source.compressed);d.status=status;d.providerId=provider.id;d.model=provider.modelId;d.protocol=provider.protocol;d.promptVersion=VisionRepository.PROMPT_VERSION;return d;}
    private String safeHash(File f){try{return sha256(f);}catch(Exception ignored){return "";}}
    private void recognizeNext(){if(queueIndex>=queue.size()){refresh();feedback("AI 图片识别队列已处理");return;}long generation=vision().currentGeneration();PendingSource source=queue.get(queueIndex);LunaDatabase.DocumentDraft d=draft(source,"PROCESSING");activeDocumentId=database().addDocument(d);refresh();feedback("正在识别第 "+source.page+" 页");try{vision().recognizeImage(bytes(source.compressed),source.uploadMime,source.page,new VisionRepository.Callback(){@Override public void success(VisionRepository.VisionDraft result){if(!vision().isCurrent(generation))return;long documentId=activeDocumentId;database().updateDocumentAi(documentId,"DRAFT",System.currentTimeMillis(),result.rawResponse,result.structuredJson,result.visibleText,"");requireActivity().runOnUiThread(()->{if(vision().isCurrent(generation))showDraft(documentId,result,generation);});}@Override public void failure(String message){if(!vision().isCurrent(generation))return;long documentId=activeDocumentId;database().updateDocumentAi(documentId,"FAILED",System.currentTimeMillis(),"","","",message);requireActivity().runOnUiThread(()->{if(!vision().isCurrent(generation))return;refresh();feedback("第 "+source.page+" 页识别失败，可稍后重试");queueIndex++;recognizeNext();});}});}catch(Exception error){if(vision().isCurrent(generation)){database().updateDocumentAi(activeDocumentId,"FAILED",System.currentTimeMillis(),"","","",host().safeMessage(error));queueIndex++;recognizeNext();}}}

    private void showDraft(long documentId, VisionRepository.VisionDraft result, long generation){LinearLayout form=verticalForm();form.addView(infoLabel("AI 只读取图片中可见文字；空白字段代表未识别。确认前可逐字段修改。"));com.google.android.material.textfield.TextInputEditText brand=addField(form,"商品名",result.brandName,false),generic=addField(form,"通用名",result.genericName,false),ingredients=addField(form,"有效成分",result.ingredients,true),strength=addField(form,"规格",result.strength,false),dosage=addField(form,"剂型",result.dosageForm,false),maker=addField(form,"厂家",result.manufacturer,false),approval=addField(form,"批准文号",result.approvalNo,false),trace=addField(form,"条码/追溯码文本",result.traceabilityCode,false),lot=addField(form,"批号",result.lotNo,false),production=addField(form,"生产日期 YYYY-MM-DD",result.productionDate,false),expiry=addField(form,"有效期 YYYY-MM-DD",result.expiryDate,false),storage=addField(form,"储存条件",result.storageConditions,true),indication=addField(form,"用途/适应症",result.indication,true),contra=addField(form,"禁忌/注意事项",result.contraindications,true),title=addField(form,"说明书标题",result.documentTitle,false),chapter=addField(form,"章节",result.chapter,false),version=addField(form,"版本/修订日期",result.version,false),links=addField(form,"官方链接（可选）","",false);ScrollView scroll=new ScrollView(requireContext());scroll.addView(form);String warning=result.uncertainFields.isEmpty()?"仍请对照原图确认。":"需要重点核对：\n"+result.uncertainFields;AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle("确认 AI 识别草稿").setMessage(warning).setView(scroll).setNegativeButton("稍后确认",(d,w)->{if(!vision().isCurrent(generation))return;queueIndex++;recognizeNext();}).setPositiveButton("确认并保存",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{if(!vision().isCurrent(generation)){dialog.dismiss();return;}if(!validDate(value(production))||!validDate(value(expiry))){feedback("日期必须为空或 YYYY-MM-DD");return;}try{String brandValue=value(brand),genericValue=value(generic);if(brandValue.isEmpty()&&genericValue.isEmpty()&&value(title).isEmpty()){feedback("至少确认商品名、通用名或说明书标题");return;}JSONObject corrected=new JSONObject().put("brandName",brandValue).put("genericName",genericValue).put("ingredients",value(ingredients)).put("strength",value(strength)).put("dosageForm",value(dosage)).put("manufacturer",value(maker)).put("approvalNo",value(approval)).put("traceabilityCode",value(trace)).put("lotNo",value(lot)).put("productionDate",value(production)).put("expiryDate",value(expiry)).put("storageConditions",value(storage)).put("indication",value(indication)).put("contraindications",value(contra)).put("title",value(title)).put("chapter",value(chapter)).put("version",value(version));long medId=0;if(!brandValue.isEmpty()||!genericValue.isEmpty()){LunaDatabase.MedicationDraft medication=new LunaDatabase.MedicationDraft();medication.brandName=brandValue;medication.genericName=genericValue;medication.ingredients=value(ingredients);medication.strength=value(strength);medication.dosageForm=value(dosage);medication.manufacturer=value(maker);medication.approvalNo=value(approval);medication.indication=value(indication);medication.contraindications=value(contra);medication.notes=value(storage)+(value(trace).isEmpty()?"":"\n条码/追溯码："+value(trace));medId=database().addMedication(medication);if(!value(lot).isEmpty()||!value(expiry).isEmpty()||!value(production).isEmpty()){LunaDatabase.BatchDraft batch=new LunaDatabase.BatchDraft();batch.lotNo=value(lot);batch.productionDate=value(production);batch.expiryDate=value(expiry);batch.storageConditions=value(storage);database().addBatch(medId,batch);}}database().updateDocumentReview(documentId,corrected.toString(),value(chapter),value(version),value(maker),value(links),true);dialog.dismiss();scheduler().rebuild();queueIndex++;refresh();feedback(medId>0?"已确认并写入药物、批次与本地资料":"已确认并保存本地资料");recognizeNext();}catch(Exception error){feedback("保存识别结果失败："+host().safeMessage(error));}}));pendingDialog=dialog;dialog.show();}
    private com.google.android.material.textfield.TextInputEditText addField(LinearLayout form,String hint,String initial,boolean multi){com.google.android.material.textfield.TextInputLayout layout=new com.google.android.material.textfield.TextInputLayout(requireContext());layout.setHint(hint);layout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);com.google.android.material.textfield.TextInputEditText edit=new com.google.android.material.textfield.TextInputEditText(requireContext());edit.setSingleLine(!multi);if(multi){edit.setMinLines(3);edit.setGravity(android.view.Gravity.TOP);}edit.setText(initial==null?"":initial);layout.addView(edit,new LinearLayout.LayoutParams(-1,multi?dp(88):dp(58)));form.addView(layout);return edit;}
    private static String value(android.widget.EditText e){return e.getText()==null?"":e.getText().toString().trim();}
    private static boolean validDate(String value){if(value==null||value.isEmpty())return true;try{java.time.LocalDate.parse(value);return value.matches("20\\d{2}-\\d{2}-\\d{2}");}catch(Exception ignored){return false;}}

    private void onDocumentAction(LunaDatabase.DocumentRow row,String action){if("delete".equals(action)){new MaterialAlertDialogBuilder(requireContext()).setTitle("删除这份资料？").setMessage("将删除本地索引、识别结果和私有目录中的相关文件，不能撤销。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{deleteFiles(row);database().deleteDocument(row.id);refresh();feedback("资料已删除");}).show();}else if("details".equals(action)){new MaterialAlertDialogBuilder(requireContext()).setTitle("AI 结构化结果").setMessage("模型："+row.model+"\n提示版本："+row.promptVersion+"\n原文件 SHA-256："+row.sha256+"\n\n"+row.structuredJson).setPositiveButton("关闭",null).show();}else retry(row);}
    private void retry(LunaDatabase.DocumentRow row){try{File original=new File(row.uri);if(row.compressedUri.isEmpty()&&row.mimeType.toLowerCase(Locale.ROOT).contains("pdf")){confirmSources(renderPdf(original));return;}File compressed=row.compressedUri.isEmpty()?normalizeImage(original):new File(row.compressedUri);List<PendingSource> sources=new ArrayList<>();sources.add(new PendingSource(original,compressed,row.mimeType, row.pageNo));confirmSources(sources);}catch(Exception error){feedback("无法重新准备原文件："+host().safeMessage(error));}}
    private void deleteFiles(LunaDatabase.DocumentRow row){deletePrivate(row.uri);deletePrivate(row.compressedUri);}
    private void deletePrivate(String path){try{if(path==null||path.isEmpty())return;File root=requireContext().getFilesDir().getCanonicalFile(),target=new File(path).getCanonicalFile();if(target.toPath().startsWith(root.toPath())&&target.isFile())target.delete();}catch(Exception ignored){}}
    private static final class PendingSource{final File original,compressed;final String sourceMime,uploadMime;final int page;PendingSource(File original,File compressed,String mime,int page){this.original=original;this.compressed=compressed;this.sourceMime=mime;this.uploadMime=compressed.getName().endsWith(".jpg")?"image/jpeg":mime;this.page=page;}}
    private android.widget.TextView infoLabel(String value){android.widget.TextView text=new android.widget.TextView(requireContext());text.setText(value);text.setTextColor(palette().secondary);text.setTextSize(14);return text;}
}
