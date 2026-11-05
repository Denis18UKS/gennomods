package dev.timefracture.layer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Durable namespace/lineage metadata for independent timelines.
 *
 * This index does NOT create or swap chunks, and MUST NOT be confused with
 * the missing isolated chunk/entity/block-entity backend. Keeping layer IDs,
 * dimensions, parentage and anchor coordinates now prevents the old
 * 21x17x21 displaced-island implementation from being the default.
 *
 * Each timeline covers the full registry of available dimensions, at the
 * SAME coordinates. Future chunk storage can key by (layerId, dimension, cx, cz).
 */
public final class LayerCatalog {
    public record Layer(String id,String parent,UUID actor,String name,
                        String homeDimension,int x,int y,int z,
                        List<String> dimensions,long createdMillis) {}

    private final Path file;
    private final Map<String,Layer> layers = new LinkedHashMap<>();

    public LayerCatalog(Path file) {
        this.file=file;
        read();
    }

    public synchronized Layer find(String id) {return layers.get(id);}
    public synchronized int size() {return layers.size();}
    public synchronized List<Layer> list(){return List.copyOf(layers.values());}

    public synchronized Layer register(String id,String parent,UUID actor,String name,
                                       String dimension,int x,int y,int z,
                                       Collection<String> dimensions) {
        if(id==null || !id.matches("T-[0-9]+"))throw new IllegalArgumentException("Invalid layer ID");
        if(layers.containsKey(id))return layers.get(id);
        if(parent==null || parent.isBlank())parent="-";
        if(!"-".equals(parent) && !layers.containsKey(parent))
            throw new IllegalArgumentException("Unknown parent temporal layer: "+parent);
        if(dimensions.isEmpty())throw new IllegalArgumentException("No loaded dimension registry");
        List<String> scope = dimensions.stream().distinct().sorted().toList();
        Layer entry = new Layer(id,parent,actor,name,dimension,x,y,z,scope,Instant.now().toEpochMilli());
        layers.put(id,entry);
        persist();
        return entry;
    }

    public synchronized String describe(String id) {
        Layer l=layers.get(id);
        if(l==null)return "No layer metadata found: "+id;
        return l.id+" ← "+l.parent+" · "+l.name+" · "+l.homeDimension+
            " · same-coordinate anchor "+l.x+" "+l.y+" "+l.z+
            " · dimensions "+l.dimensions.size()+
            " · chunk isolation NOT YET IMPLEMENTED";
    }

    private static String enc(String x){
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(x.getBytes(StandardCharsets.UTF_8));
    }
    private static String dec(String x){
        return new String(Base64.getUrlDecoder().decode(x),StandardCharsets.UTF_8);
    }

    private synchronized void persist(){
        try{
            Files.createDirectories(file.getParent());
            List<String> text=new ArrayList<>();
            text.add("TIMEFRACTURE_LAYER_INDEX_V1");
            for(Layer l:layers.values())
                text.add(String.join("\t",l.id,l.parent,l.actor.toString(),enc(l.name),
                     enc(l.homeDimension),Integer.toString(l.x),Integer.toString(l.y),
                     Integer.toString(l.z),Long.toString(l.createdMillis),
                     enc(String.join("\n",l.dimensions))));
            Path temporary=file.resolveSibling(file.getFileName()+".tmp");
            Files.write(temporary,text,StandardCharsets.UTF_8);
            try{
                Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }catch(AtomicMoveNotSupportedException ignored){
                Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
            }
        }catch(IOException ex){throw new IllegalStateException("Could not save temporal layer index",ex);}
    }

    private void read(){
        if(!Files.exists(file))return;
        try{
            List<String> rows=Files.readAllLines(file,StandardCharsets.UTF_8);
            if(rows.isEmpty()||!rows.getFirst().equals("TIMEFRACTURE_LAYER_INDEX_V1"))
                throw new IOException("Unsupported layer index format");
            for(int row=1;row<rows.size();row++){
                String[] a=rows.get(row).split("\t",-1);
                if(a.length!=10)throw new IOException("Invalid layer metadata at row "+row);
                Layer l=new Layer(a[0],a[1],UUID.fromString(a[2]),dec(a[3]),dec(a[4]),
                    Integer.parseInt(a[5]),Integer.parseInt(a[6]),Integer.parseInt(a[7]),
                    List.of(dec(a[9]).split("\n")),Long.parseLong(a[8]));
                layers.put(l.id,l);
            }
        }catch(Exception ex){throw new IllegalStateException("Cannot read temporal layer index. No world has been modified.",ex);}
    }
}
