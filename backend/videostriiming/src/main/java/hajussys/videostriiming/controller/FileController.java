package hajussys.videostriiming.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hajussys.videostriiming.models.FileModel;
import hajussys.videostriiming.registry.UserRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/")
@CrossOrigin("*")
public class FileController {

    @Autowired
    UserRegistry userRegistry;
    private Gson gson = new GsonBuilder().create();

    @GetMapping("/past-streams")
    private List<FileModel> getStreams() {
        System.out.println("USERS" + userRegistry.pastStreams);
        return userRegistry.pastStreams;
    }
}
