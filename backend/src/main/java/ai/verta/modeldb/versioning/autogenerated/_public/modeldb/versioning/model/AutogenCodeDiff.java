// THIS FILE IS AUTO-GENERATED. DO NOT EDIT
package ai.verta.modeldb.versioning.autogenerated._public.modeldb.versioning.model;

import ai.verta.modeldb.ModelDBException;
import ai.verta.modeldb.versioning.*;
import ai.verta.modeldb.versioning.blob.diff.*;
import ai.verta.modeldb.versioning.blob.diff.Function3;
import ai.verta.modeldb.versioning.blob.visitors.Visitor;
import com.pholser.junit.quickcheck.generator.*;
import com.pholser.junit.quickcheck.random.*;
import java.util.*;
import java.util.function.Function;

public class AutogenCodeDiff implements ProtoType {
  private AutogenGitCodeDiff Git;
  private AutogenNotebookCodeDiff Notebook;

  public AutogenCodeDiff() {
    this.Git = null;
    this.Notebook = null;
  }

  public Boolean isEmpty() {
    if (this.Git != null && !this.Git.equals(null)) {
      return false;
    }
    if (this.Notebook != null && !this.Notebook.equals(null)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"class\": \"AutogenCodeDiff\", \"fields\": {");
    boolean first = true;
    if (this.Git != null && !this.Git.equals(null)) {
      if (!first) sb.append(", ");
      sb.append("\"Git\": " + Git);
      first = false;
    }
    if (this.Notebook != null && !this.Notebook.equals(null)) {
      if (!first) sb.append(", ");
      sb.append("\"Notebook\": " + Notebook);
      first = false;
    }
    sb.append("}}");
    return sb.toString();
  }

  // TODO: actually hash
  public String getSHA() {
    StringBuilder sb = new StringBuilder();
    sb.append("AutogenCodeDiff");
    if (this.Git != null && !this.Git.equals(null)) {
      sb.append("::Git::").append(Git);
    }
    if (this.Notebook != null && !this.Notebook.equals(null)) {
      sb.append("::Notebook::").append(Notebook);
    }

    return sb.toString();
  }

  // TODO: not consider order on lists
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!(o instanceof AutogenCodeDiff)) return false;
    AutogenCodeDiff other = (AutogenCodeDiff) o;

    {
      Function3<AutogenGitCodeDiff, AutogenGitCodeDiff, Boolean> f = (x, y) -> x.equals(y);
      if (this.Git != null || other.Git != null) {
        if (this.Git == null && other.Git != null) return false;
        if (this.Git != null && other.Git == null) return false;
        if (!f.apply(this.Git, other.Git)) return false;
      }
    }
    {
      Function3<AutogenNotebookCodeDiff, AutogenNotebookCodeDiff, Boolean> f =
          (x, y) -> x.equals(y);
      if (this.Notebook != null || other.Notebook != null) {
        if (this.Notebook == null && other.Notebook != null) return false;
        if (this.Notebook != null && other.Notebook == null) return false;
        if (!f.apply(this.Notebook, other.Notebook)) return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.Git, this.Notebook);
  }

  public AutogenCodeDiff setGit(AutogenGitCodeDiff value) {
    this.Git = Utils.removeEmpty(value);
    return this;
  }

  public AutogenGitCodeDiff getGit() {
    return this.Git;
  }

  public AutogenCodeDiff setNotebook(AutogenNotebookCodeDiff value) {
    this.Notebook = Utils.removeEmpty(value);
    return this;
  }

  public AutogenNotebookCodeDiff getNotebook() {
    return this.Notebook;
  }

  public static AutogenCodeDiff fromProto(ai.verta.modeldb.versioning.CodeDiff blob) {
    if (blob == null) {
      return null;
    }

    AutogenCodeDiff obj = new AutogenCodeDiff();
    {
      Function<ai.verta.modeldb.versioning.CodeDiff, AutogenGitCodeDiff> f =
          x -> AutogenGitCodeDiff.fromProto(blob.getGit());
      obj.Git = Utils.removeEmpty(f.apply(blob));
    }
    {
      Function<ai.verta.modeldb.versioning.CodeDiff, AutogenNotebookCodeDiff> f =
          x -> AutogenNotebookCodeDiff.fromProto(blob.getNotebook());
      obj.Notebook = Utils.removeEmpty(f.apply(blob));
    }
    return obj;
  }

  public ai.verta.modeldb.versioning.CodeDiff.Builder toProto() {
    ai.verta.modeldb.versioning.CodeDiff.Builder builder =
        ai.verta.modeldb.versioning.CodeDiff.newBuilder();
    {
      if (this.Git != null && !this.Git.equals(null)) {
        Function<ai.verta.modeldb.versioning.CodeDiff.Builder, Void> f =
            x -> {
              builder.setGit(this.Git.toProto());
              return null;
            };
        f.apply(builder);
      }
    }
    {
      if (this.Notebook != null && !this.Notebook.equals(null)) {
        Function<ai.verta.modeldb.versioning.CodeDiff.Builder, Void> f =
            x -> {
              builder.setNotebook(this.Notebook.toProto());
              return null;
            };
        f.apply(builder);
      }
    }
    return builder;
  }

  public void preVisitShallow(Visitor visitor) throws ModelDBException {
    visitor.preVisitAutogenCodeDiff(this);
  }

  public void preVisitDeep(Visitor visitor) throws ModelDBException {
    this.preVisitShallow(visitor);
    visitor.preVisitDeepAutogenGitCodeDiff(this.Git);
    visitor.preVisitDeepAutogenNotebookCodeDiff(this.Notebook);
  }

  public AutogenCodeDiff postVisitShallow(Visitor visitor) throws ModelDBException {
    return visitor.postVisitAutogenCodeDiff(this);
  }

  public AutogenCodeDiff postVisitDeep(Visitor visitor) throws ModelDBException {
    this.setGit(visitor.postVisitDeepAutogenGitCodeDiff(this.Git));
    this.setNotebook(visitor.postVisitDeepAutogenNotebookCodeDiff(this.Notebook));
    return this.postVisitShallow(visitor);
  }
}