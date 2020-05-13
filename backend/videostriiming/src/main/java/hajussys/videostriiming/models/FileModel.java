package hajussys.videostriiming.models;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class FileModel {
    String id;
    Date date;
}
